package cl.jeisell.alertarutas

import android.app.Notification
import android.content.ComponentName
import android.os.Bundle
import android.service.notification.NotificationListenerService
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

    override fun onListenerConnected() {
        super.onListenerConnected()
        atendidas.clear()
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

        // el registro se guarda siempre, incluso si no corresponde sonar:
        // asi se puede revisar despues que avisos quedaron filtrados
        Prefs.registrar(ctx, titulo, texto, recibida, estado, extra)

        if (!activa) return
        if (!esOferta) return

        // el aviso con el boton de abrir aparece siempre; la alarma, solo en horario
        Aviso.mostrar(ctx, titulo, texto, recibida, extra)

        if (enHorario) {
            Alarma.sonar(ctx, Prefs.duracionAlarma(ctx))
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // no se usa
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
    }
}
