package cl.jeisell.alertarutas

import android.app.Notification
import android.content.ComponentName
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification

/**
 * Escucha las notificaciones del sistema y reacciona solo a las de Envios Extra.
 * El sistema mantiene este servicio vivo mientras el permiso de "acceso a
 * notificaciones" este concedido; no hace falta dejar la app abierta.
 */
class RutaListenerService : NotificationListenerService() {

    /**
     * Claves de las notificaciones ya atendidas.
     *
     * Android reenvia la misma notificacion cada vez que la app la actualiza,
     * y sin esto la alarma sonaria varias veces por una sola oferta. Antes el
     * filtro descartaba cualquier aviso con el mismo texto dentro de un minuto,
     * y eso se tragaba ofertas reales seguidas con texto igual. Ahora se
     * compara la identidad completa de la notificacion, no solo su texto.
     */
    private val atendidas = LinkedHashSet<String>()

    /** Ofertas que estan en la bandeja ahora: clave -> cuando aparecieron. */
    private val vivas = HashMap<String, Long>()

    /**
     * Clave de la oferta que tiene la alarma sonando ahora mismo.
     *
     * Sirve para callarla en cuanto esa oferta desaparece de la bandeja: si
     * Mercado Libre la retiro, ya no hay nada que correr a tomar.
     */
    private var sonandoPor: String? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        atendidas.clear()
        vivas.clear()
        sonandoPor = null
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // si Android desconecta el servicio, pedir volver a engancharse
        try {
            requestRebind(ComponentName(this, RutaListenerService::class.java))
        } catch (e: Exception) {
            // nada: si no se puede, el sistema lo reconecta al reiniciar
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notificacion = sbn ?: return
        if (notificacion.packageName != Prefs.PAQUETE_ENVIOS_EXTRA) return

        val n = notificacion.notification ?: return
        // los resumenes de grupo repiten el contenido de otra notificacion
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        val titulo = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val texto = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        // Todo lo demas que puede traer la notificacion y no se ve en la barra:
        // el texto expandido, el subtitulo, el resumen y las lineas de lista.
        // Si Envios Extra escribe ahi el dia del recorrido, aqui aparece.
        val extra = textoEscondido(extras, titulo, texto)

        if (titulo.isBlank() && texto.isBlank() && extra.isBlank()) return

        val recibida = notificacion.postTime
        val clave = "${notificacion.key}|$recibida|$titulo|$texto|$extra"
        if (!marcarComoAtendida(clave)) return

        val ctx = applicationContext
        val activa = Prefs.alertaActiva(ctx)
        val enHorario = Prefs.enHorario(ctx)
        val esOferta = !Prefs.soloOfertas(ctx) || Filtro.esOferta(
            titulo,
            "$texto $extra",
            Prefs.palabrasOferta(ctx),
            Prefs.palabrasIgnorar(ctx)
        )

        val estado = when {
            !activa -> Prefs.ESTADO_ALERTA_APAGADA
            !esOferta -> Prefs.ESTADO_NO_ES_OFERTA
            !enHorario -> Prefs.ESTADO_FUERA_HORARIO
            else -> Prefs.ESTADO_SONO
        }

        // PRIMERO LA ALERTA, DESPUES ANOTAR.
        // Guardar el registro obliga a leer y reescribir todo el historial, y
        // eso son milisegundos que antes se gastaban ANTES de que sonara la
        // alarma. Ahora la persona se entera primero y el papeleo va despues,
        // ademas en un hilo aparte para que nunca estorbe.
        val ahora = System.currentTimeMillis()

        if (activa && esOferta) {
            // contar es barato (dos valores guardados), asi que se puede hacer
            // antes de sonar y mostrarlo en la alerta
            val (numero, desdeAnterior) = Prefs.contarOferta(ctx, ahora, Respaldo.hoy())

            // guardar cuando empezo esta oferta, para medir despues cuanto duro
            vivas[notificacion.key] = ahora

            // el aviso con el boton de abrir aparece siempre; la alarma, solo en horario
            Aviso.mostrar(ctx, titulo, texto, recibida, extra, numero, desdeAnterior)
            if (enHorario) {
                Alarma.sonar(ctx, Prefs.duracionAlarma(ctx))
                sonandoPor = notificacion.key

                if (Prefs.vozActiva(ctx)) {
                    val horario = Filtro.horarioDelTexto(titulo, texto, extra)
                    Voz.decir(ctx, Voz.frase(numero, horario))
                }
            }
        }

        // el registro se guarda siempre, incluso cuando no corresponde sonar:
        // asi se puede revisar despues que avisos quedaron filtrados
        anotarEnSegundoPlano(
            ctx, titulo, texto, recibida, estado, extra, ahora, notificacion.key
        )
    }

    /**
     * Cuando la notificacion de la oferta desaparece de la bandeja.
     *
     * Aqui esta el dato que de verdad interesa: cuanto tiempo estuvo viva la
     * oferta. Si la retiro Mercado Libre, es senal de que alguien la tomo o
     * vencio, y esa es la ventana real que hay para reaccionar. Si la quitaste
     * tu al abrirla o descartarla, no dice nada del tiempo disponible, por eso
     * se anota el motivo junto con la duracion.
     */
    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
        reason: Int
    ) {
        val notificacion = sbn ?: return
        if (notificacion.packageName != Prefs.PAQUETE_ENVIOS_EXTRA) return

        val clave = notificacion.key

        // Si la alarma estaba sonando por esta oferta, callarla: la oferta ya
        // no esta en la bandeja, asi que no hay nada que ir a tomar. Esto
        // tambien cubre el caso de que la persona descarte la notificacion.
        if (sonandoPor == clave) {
            sonandoPor = null
            Voz.callar()
            Alarma.detener()
            Aviso.quitar(applicationContext)
        }

        val inicio = vivas.remove(clave) ?: return
        val duro = System.currentTimeMillis() - inicio
        if (duro < 0) return

        val motivo = motivoDelFin(reason)
        val ctx = applicationContext
        try {
            escribano.execute {
                try {
                    Prefs.cerrarOferta(ctx, clave, duro, motivo)
                } catch (e: Exception) {
                    // el dato de duracion es util, no critico
                }
            }
        } catch (e: Exception) {
            // nada
        }
    }

    private fun motivoDelFin(reason: Int): String = when (reason) {
        REASON_APP_CANCEL, REASON_APP_CANCEL_ALL ->
            "la retiro Mercado Libre"
        REASON_TIMEOUT ->
            "vencio"
        REASON_CLICK ->
            "la abriste"
        REASON_CANCEL, REASON_CANCEL_ALL, REASON_LISTENER_CANCEL,
        REASON_LISTENER_CANCEL_ALL ->
            "la descartaste"
        else ->
            "otro motivo"
    }

    /**
     * Anota la oferta fuera del camino critico.
     *
     * Se usa un solo hilo en fila para que las anotaciones no se pisen entre
     * ellas cuando llegan dos avisos casi juntos.
     */
    private fun anotarEnSegundoPlano(
        ctx: android.content.Context,
        titulo: String,
        texto: String,
        recibida: Long,
        estado: String,
        extra: String,
        momento: Long,
        clave: String
    ) {
        try {
            escribano.execute {
                try {
                    Prefs.registrar(
                        ctx, titulo, texto, recibida, estado, extra, momento, clave
                    )
                } catch (e: Exception) {
                    // si falla la anotacion, la alerta ya se dio igual
                }
            }
        } catch (e: Exception) {
            // si no se puede encolar, anotar aqui mismo antes que perder el dato
            Prefs.registrar(ctx, titulo, texto, recibida, estado, extra, momento, clave)
        }
    }

    /**
     * Junta todos los campos de texto de la notificacion que no son el titulo
     * ni el texto corto: el texto expandido, el subtitulo, el resumen, el
     * titulo grande y las lineas de lista. Se devuelve sin repetidos.
     */
    private fun textoEscondido(extras: Bundle?, titulo: String, texto: String): String {
        if (extras == null) return ""

        val claves = listOf(
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_TITLE_BIG,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
            Notification.EXTRA_INFO_TEXT
        )

        val partes = LinkedHashSet<String>()
        for (clave in claves) {
            val valor = extras.getCharSequence(clave)?.toString()?.trim().orEmpty()
            if (valor.isNotEmpty() && valor != titulo && valor != texto) partes.add(valor)
        }

        val lineas: Array<out CharSequence?> =
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) ?: emptyArray()
        for (indice in lineas.indices) {
            val valor = lineas[indice]?.toString()?.trim().orEmpty()
            if (valor.isNotEmpty() && valor != titulo && valor != texto) partes.add(valor)
        }

        return partes.joinToString(" · ")
    }

    /** Devuelve true si la notificacion es nueva y hay que atenderla. */
    private fun marcarComoAtendida(clave: String): Boolean {
        if (atendidas.contains(clave)) return false
        atendidas.add(clave)
        while (atendidas.size > MAX_ATENDIDAS) {
            val masAntigua = atendidas.firstOrNull() ?: break
            atendidas.remove(masAntigua)
        }
        return true
    }

    private companion object {
        const val MAX_ATENDIDAS = 60

        /** Hilo unico donde se escriben las anotaciones, fuera del camino critico. */
        val escribano: java.util.concurrent.ExecutorService =
            java.util.concurrent.Executors.newSingleThreadExecutor()
    }
}
