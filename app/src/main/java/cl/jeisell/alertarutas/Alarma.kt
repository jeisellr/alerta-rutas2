package cl.jeisell.alertarutas

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Suena una alarma fuerte y hace vibrar el telefono.
 * Usa el canal de ALARMA, que en Android se escucha incluso con el timbre
 * en silencio y, por defecto, tambien en modo No molestar.
 */
object Alarma {

    /**
     * Tope de seguridad cuando la alarma esta en modo "hasta que la apague":
     * cinco minutos. Sin esto, un telefono olvidado en el bolsillo quedaria
     * sonando y gastando bateria toda la tarde.
     */
    private const val TOPE_SIN_LIMITE_MS = 5 * 60 * 1000L

    private val handler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var volumenPrevio = -1
    private var contexto: Context? = null

    private val detenerSolo = Runnable { detener() }

    /**
     * @param segundos cuanto debe sonar. 0 o menos = hasta que la silencien
     *                 (con el tope de seguridad de [TOPE_SIN_LIMITE_MS]).
     */
    @Synchronized
    fun sonar(context: Context, segundos: Int) {
        val ctx = context.applicationContext
        contexto = ctx
        detenerReproduccion()
        handler.removeCallbacks(detenerSolo)

        subirVolumenAlarma(ctx)
        reproducir(ctx)
        vibrar(ctx)

        handler.postDelayed(detenerSolo, duracionMs(segundos))
    }

    @Synchronized
    fun detener() {
        handler.removeCallbacks(detenerSolo)
        detenerReproduccion()
        val ctx = contexto ?: return
        vibrador(ctx)?.cancel()
        restaurarVolumen(ctx)
    }

    fun sonando(): Boolean = player != null

    /** Cuanto va a sonar, en milisegundos, para una duracion pedida en segundos. */
    fun duracionMs(segundos: Int): Long =
        if (segundos <= 0) TOPE_SIN_LIMITE_MS else segundos.coerceIn(3, 120) * 1000L

    // ---------------------------------------------------------------- interno

    private fun reproducir(ctx: Context) {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(ctx, uri)
            mp.isLooping = true
            mp.prepare()
            mp.start()
            player = mp
        } catch (e: Exception) {
            player = null
        }
    }

    private fun detenerReproduccion() {
        val mp = player ?: return
        player = null
        try {
            if (mp.isPlaying) mp.stop()
        } catch (e: Exception) {
            // nada: el player ya estaba en un estado invalido
        }
        try {
            mp.release()
        } catch (e: Exception) {
            // nada
        }
    }

    private fun vibrar(ctx: Context) {
        val v = vibrador(ctx) ?: return
        if (!v.hasVibrator()) return
        val patron = longArrayOf(0, 800, 400)
        try {
            // el 0 final repite el patron hasta que se cancela
            v.vibrate(VibrationEffect.createWaveform(patron, 0))
        } catch (e: Exception) {
            // nada
        }
    }

    private fun vibrador(ctx: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun audio(ctx: Context): AudioManager? =
        ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private fun subirVolumenAlarma(ctx: Context) {
        val am = audio(ctx) ?: return
        try {
            if (volumenPrevio < 0) {
                volumenPrevio = am.getStreamVolume(AudioManager.STREAM_ALARM)
            }
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )
        } catch (e: Exception) {
            // algunos equipos bloquean el cambio de volumen; la alarma suena igual
        }
    }

    private fun restaurarVolumen(ctx: Context) {
        val am = audio(ctx) ?: return
        val previo = volumenPrevio
        volumenPrevio = -1
        if (previo < 0) return
        try {
            am.setStreamVolume(AudioManager.STREAM_ALARM, previo, 0)
        } catch (e: Exception) {
            // nada
        }
    }
}
