package cl.jeisell.alertarutas

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Dice la oferta en voz alta, para enterarse sin mirar el telefono.
 *
 * Habla por el canal de alarma, asi que se escucha aunque el timbre este en
 * silencio. Mientras habla, baja el volumen de la alarma y lo devuelve al
 * terminar: si no, la sirena se come las palabras.
 */
object Voz {

    private const val ID_FRASE = "oferta"

    private var motor: TextToSpeech? = null
    private var listo = false
    private var pendiente: String? = null

    @Synchronized
    fun decir(context: Context, frase: String) {
        if (frase.isBlank()) return
        val ctx = context.applicationContext

        val actual = motor
        if (listo && actual != null) {
            hablar(actual, frase)
            return
        }

        // todavia no esta listo: se guarda y se dice apenas arranque
        pendiente = frase
        if (actual != null) return

        try {
            motor = TextToSpeech(ctx) { estado ->
                if (estado != TextToSpeech.SUCCESS) return@TextToSpeech
                val arrancado = motor ?: return@TextToSpeech
                try {
                    arrancado.language = Locale("es", "CL")
                } catch (e: Exception) {
                    // si no hay voz en espanol de Chile, queda la del sistema
                }
                listo = true
                val guardada = pendiente
                pendiente = null
                if (guardada != null) hablar(arrancado, guardada)
            }
        } catch (e: Exception) {
            motor = null
        }
    }

    /** Corta la voz, por ejemplo cuando se silencia la alarma. */
    @Synchronized
    fun callar() {
        try {
            motor?.stop()
        } catch (e: Exception) {
            // nada
        }
        Alarma.atenuar(false)
    }

    /** Arma la frase corta que se va a escuchar. */
    fun frase(numero: Int, horario: String?): String {
        val partes = ArrayList<String>()
        partes.add("Oferta de ruta disponible")
        if (numero > 0) partes.add("numero $numero de hoy")
        if (!horario.isNullOrBlank()) partes.add("recorrido $horario")
        return partes.joinToString(". ") + "."
    }

    private fun hablar(arrancado: TextToSpeech, frase: String) {
        try {
            arrancado.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            arrancado.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Alarma.atenuar(true)
                }

                override fun onDone(utteranceId: String?) {
                    Alarma.atenuar(false)
                }

                @Deprecated("Android la pide igual aunque este obsoleta")
                override fun onError(utteranceId: String?) {
                    Alarma.atenuar(false)
                }
            })
            arrancado.speak(frase, TextToSpeech.QUEUE_FLUSH, null, ID_FRASE)
        } catch (e: Exception) {
            Alarma.atenuar(false)
        }
    }
}
