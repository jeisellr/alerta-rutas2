package cl.jeisell.alertarutas

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Escucha las notificaciones del sistema y reacciona solo a las de Envios Extra.
 * El sistema mantiene este servicio vivo mientras el permiso de "acceso a
 * notificaciones" este concedido; no hace falta dejar la app abierta.
 */
class RutaListenerService : NotificationListenerService() {

    private var ultimaFirma = ""
    private var ultimoMomento = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notificacion = sbn ?: return
        if (notificacion.packageName != Prefs.PAQUETE_ENVIOS_EXTRA) return

        val n = notificacion.notification ?: return
        // los resumenes de grupo repiten el contenido de otra notificacion
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        val titulo = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val texto = (extras?.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()

        if (titulo.isBlank() && texto.isBlank()) return

        // Android reenvia la misma notificacion cuando se actualiza: no sonar dos veces
        val firma = "$titulo|$texto"
        val ahora = System.currentTimeMillis()
        if (firma == ultimaFirma && ahora - ultimoMomento < 60_000L) return
        ultimaFirma = firma
        ultimoMomento = ahora

        val ctx = applicationContext

        // el registro se guarda siempre, incluso fuera del horario de trabajo
        Prefs.registrar(ctx, titulo, texto)

        if (!Prefs.alertaActiva(ctx)) return

        // el aviso con el boton de abrir aparece siempre; la alarma, solo en horario
        Aviso.mostrar(ctx, titulo, texto)

        if (Prefs.enHorario(ctx)) {
            Alarma.sonar(ctx, Prefs.duracionAlarma(ctx))
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // no se usa
    }
}
