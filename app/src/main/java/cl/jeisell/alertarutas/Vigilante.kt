package cl.jeisell.alertarutas

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.service.notification.NotificationListenerService

/**
 * Vuelve a enganchar el servicio que escucha las notificaciones.
 *
 * Android puede soltar ese servicio en tres momentos en que nadie avisa:
 * al reiniciar el telefono, al instalar una version nueva de la app, y a
 * veces por su cuenta cuando limpia memoria. Si eso pasa, la app deja de
 * avisar en silencio, que es la peor forma de fallar.
 *
 * Este vigilante se despierta en esos momentos y pide volver a conectarse,
 * ademas de una revision cada media hora por si acaso.
 */
class Vigilante : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context?.applicationContext ?: return
        reenganchar(ctx)
        programarRevision(ctx)
    }

    companion object {

        const val ACCION_REVISAR = "cl.jeisell.alertarutas.REVISAR"

        private const val CADA_MEDIA_HORA = 30 * 60 * 1000L
        private const val CODIGO = 7001

        /** Le pide a Android que vuelva a conectar el servicio de notificaciones. */
        fun reenganchar(ctx: Context) {
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(ctx, RutaListenerService::class.java)
                )
            } catch (e: Exception) {
                // si no se puede ahora, se reintenta en la proxima revision
            }
        }

        /** Deja programada una revision periodica. Repetirla no duplica nada. */
        fun programarRevision(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(ctx, Vigilante::class.java)
            intent.action = ACCION_REVISAR
            val pendiente = PendingIntent.getBroadcast(
                ctx,
                CODIGO,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                // inexacta a proposito: no necesita precision y gasta menos bateria
                am.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + CADA_MEDIA_HORA,
                    CADA_MEDIA_HORA,
                    pendiente
                )
            } catch (e: Exception) {
                // nada: la app sigue funcionando sin la revision periodica
            }
        }
    }
}
