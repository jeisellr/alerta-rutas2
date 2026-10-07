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

    private const val CANAL = "ofertas_ruta"

    fun mostrar(ctx: Context, titulo: String, texto: String) {
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
        val piPantallaCompleta = PendingIntent.getActivity(
            ctx, 3, pantallaCompleta, banderas()
        )

        val icono = Icon.createWithResource(ctx, R.drawable.ic_app)
        val cuerpo = if (texto.isBlank()) "Toca para abrir Envios Extra" else texto

        val constructor = Notification.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle(if (titulo.isBlank()) "Oferta de ruta" else titulo)
            .setContentText(cuerpo)
            .setStyle(Notification.BigTextStyle().bigText(cuerpo))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
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
        // el sonido y la vibracion los maneja Alarma.kt, para no sonar dos veces
        canal.setSound(null, null)
        canal.enableVibration(false)
        canal.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(canal)
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
