package cl.jeisell.alertarutas

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Apaga la alarma cuando se toca "Silenciar" en el aviso. */
class SilenciarReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        Alarma.detener()
        val ctx = context ?: return
        Aviso.quitar(ctx.applicationContext)
    }
}
