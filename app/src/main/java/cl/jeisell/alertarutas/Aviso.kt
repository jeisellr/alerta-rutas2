package cl.jeisell.alertarutas

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build

/**
 * Muestra el aviso propio de la app: aparece arriba en la pantalla con un boton
 * que abre Envios Extra de un toque, para no perder segundos buscando la app.
 *
 * El aviso solo ABRE la app. No toca botones ni acepta rutas: eso lo hace
 * la persona.
 */
object Aviso {

    const val ID_AVISO = 2001
    const val EXTRA_TITULO = "titulo"
    const val EXTRA_TEXTO = "texto"
    const val EXTRA_MOMENTO = "momento"
    const val EXTRA_DETALLE = "detalle"
    const val EXTRA_NUMERO = "numero"
    const val EXTRA_DESDE = "desde"

    /** "Oferta N°3 de hoy · la anterior hace 23 min", o vacio si no hay datos. */
    fun resumenDelDia(numero: Int, desdeAnterior: Long): String {
        if (numero <= 0) return ""
        val partes = ArrayList<String>()
        partes.add("Oferta N°$numero de hoy")
        if (desdeAnterior > 0) {
            partes.add("la anterior hace ${enPalabras(desdeAnterior)}")
        }
        return partes.joinToString(" · ")
    }

    /** Convierte milisegundos a algo legible: "45 s", "23 min", "2 h 10 min". */
    fun enPalabras(ms: Long): String {
        if (ms < 0) return ""
        val segundos = ms / 1000
        if (segundos < 60) return "$segundos s"
        val minutos = segundos / 60
        if (minutos < 60) return "$minutos min"
        val horas = minutos / 60
        val resto = minutos % 60
        return if (resto == 0L) "$horas h" else "$horas h $resto min"
    }

    private const val CANAL = "ofertas_ruta_2"
    private const val CANAL_VIEJO = "ofertas_ruta"

    fun mostrar(
        ctx: Context,
        titulo: String,
        texto: String,
        recibida: Long = 0L,
        extra: String = "",
        numero: Int = 0,
        desdeAnterior: Long = -1L
    ) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        crearCanal(nm)

        val piAbrir = PendingIntent.getActivity(
            ctx, 1, intentAbrirEnviosExtra(ctx), banderas()
        )
        val piSilenciar = PendingIntent.getBroadcast(
            ctx,
            2,
            Intent(ctx, SilenciarReceiver::class.java),
            banderas()
        )

        // pantalla completa estilo llamada entrante: enciende la pantalla
        val pantallaCompleta = Intent(ctx, AlertaActivity::class.java)
        pantallaCompleta.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        pantallaCompleta.putExtra(EXTRA_TITULO, titulo)
        pantallaCompleta.putExtra(EXTRA_TEXTO, texto)
        pantallaCompleta.putExtra(EXTRA_MOMENTO, recibida)
        pantallaCompleta.putExtra(EXTRA_DETALLE, extra)
        pantallaCompleta.putExtra(EXTRA_NUMERO, numero)
        pantallaCompleta.putExtra(EXTRA_DESDE, desdeAnterior)
        val piPantallaCompleta = PendingIntent.getActivity(
            ctx, 3, pantallaCompleta, banderas()
        )

        val icono = Icon.createWithResource(ctx, R.drawable.ic_app)
        val horario = Filtro.horarioDelTexto(titulo, texto, extra)
        val base = when {
            texto.isNotBlank() && extra.isNotBlank() -> "$texto\n$extra"
            texto.isNotBlank() -> texto
            extra.isNotBlank() -> extra
            else -> "Toca para abrir Envios Extra"
        }
        val resumen = resumenDelDia(numero, desdeAnterior)
        val conHorario = if (horario != null) "Recorrido: $horario\n$base" else base
        val cuerpo = if (resumen.isEmpty()) conHorario else "$resumen\n$conHorario"

        val constructor = Notification.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle(if (titulo.isBlank()) "Oferta de ruta" else titulo)
            .setContentText(cuerpo)
            .setStyle(Notification.BigTextStyle().bigText(cuerpo))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            // false = el aviso tambien viaja al reloj emparejado
            .setLocalOnly(false)
            .setContentIntent(piAbrir)
            .setFullScreenIntent(piPantallaCompleta, true)
            .addAction(
                Notification.Action.Builder(icono, "Abrir Envios Extra", piAbrir).build()
            )
            .addAction(
                Notification.Action.Builder(icono, "Silenciar", piSilenciar).build()
            )

        try {
            nm.notify(ID_AVISO, constructor.build())
        } catch (e: Exception) {
            // si falta el permiso de notificaciones, la alarma suena igual
        }
    }

    /**
     * Desde Android 14 el aviso a pantalla completa necesita un permiso aparte
     * que la persona concede en los ajustes de la app.
     */
    fun puedeUsarPantallaCompleta(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return true
        return nm.canUseFullScreenIntent()
    }

    fun quitar(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        try {
            nm.cancel(ID_AVISO)
        } catch (e: Exception) {
            // nada
        }
    }

    // ---------------------------------------------------------------- interno

    private fun crearCanal(nm: NotificationManager) {
        val canal = NotificationChannel(
            CANAL,
            "Ofertas de ruta",
            NotificationManager.IMPORTANCE_HIGH
        )
        canal.description = "Aviso en pantalla cuando llega una oferta de ruta"
        // el sonido lo maneja Alarma.kt, para no sonar dos veces
        canal.setSound(null, null)
        // la vibracion del canal es la que hace vibrar el reloj emparejado;
        // en el telefono se suma a la de Alarma.kt y casi no se nota
        canal.enableVibration(true)
        canal.vibrationPattern = longArrayOf(0, 600, 300, 600)
        canal.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(canal)

        // el canal anterior no vibraba y por eso el reloj quedaba mudo:
        // los canales no se pueden modificar, hay que reemplazarlos
        try {
            nm.deleteNotificationChannel(CANAL_VIEJO)
        } catch (e: Exception) {
            // nada
        }
    }

    private fun intentAbrirEnviosExtra(ctx: Context): Intent {
        val intent = ctx.packageManager.getLaunchIntentForPackage(Prefs.PAQUETE_ENVIOS_EXTRA)
            ?: Intent(ctx, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    private fun banderas(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
