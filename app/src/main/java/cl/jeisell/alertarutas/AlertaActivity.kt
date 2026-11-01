package cl.jeisell.alertarutas

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla de aviso a pantalla completa, estilo llamada entrante.
 *
 * Se muestra sobre la pantalla bloqueada y enciende la pantalla sola, para que
 * la oferta se vea sin buscar nada. El boton grande ABRE Envios Extra: aceptar
 * la ruta sigue siendo un toque de la persona dentro de esa app.
 */
class AlertaActivity : Activity() {

    private lateinit var tvTitulo: TextView
    private lateinit var tvTexto: TextView
    private lateinit var tvMomento: TextView
    private lateinit var tvHorario: TextView
    private lateinit var tvResumen: TextView

    private val formatoMomento = SimpleDateFormat("EEEE d 'de' MMMM · HH:mm", Locale("es", "CL"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // encender la pantalla y aparecer aunque este bloqueada
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_alerta)

        tvTitulo = findViewById(R.id.tvAlertaTitulo)
        tvTexto = findViewById(R.id.tvAlertaTexto)
        tvMomento = findViewById(R.id.tvAlertaMomento)
        tvHorario = findViewById(R.id.tvAlertaHorario)
        tvResumen = findViewById(R.id.tvAlertaResumen)

        findViewById<Button>(R.id.btnAlertaAbrir).setOnClickListener { abrirConDesbloqueo() }
        findViewById<Button>(R.id.btnAlertaSilenciar).setOnClickListener { silenciar() }

        pintar(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)
        pintar(intent)
    }

    private fun pintar(intent: Intent?) {
        val titulo = intent?.getStringExtra(Aviso.EXTRA_TITULO).orEmpty()
        val texto = intent?.getStringExtra(Aviso.EXTRA_TEXTO).orEmpty()
        val detalle = intent?.getStringExtra(Aviso.EXTRA_DETALLE).orEmpty()
        tvTitulo.text = if (titulo.isBlank()) "Oferta de ruta" else titulo
        tvTexto.text = when {
            texto.isNotBlank() && detalle.isNotBlank() -> "$texto\n$detalle"
            texto.isNotBlank() -> texto
            detalle.isNotBlank() -> detalle
            else -> "Toca para abrir Envios Extra"
        }

        // cuando llego el aviso: dia de la semana y hora, en grande
        val momento = intent?.getLongExtra(Aviso.EXTRA_MOMENTO, 0L) ?: 0L
        val cuando = if (momento > 0L) Date(momento) else Date()
        tvMomento.text = conMayuscula(formatoMomento.format(cuando))

        // cuantas ofertas van hoy y hace cuanto fue la anterior
        val numero = intent?.getIntExtra(Aviso.EXTRA_NUMERO, 0) ?: 0
        val desde = intent?.getLongExtra(Aviso.EXTRA_DESDE, -1L) ?: -1L
        val resumen = Aviso.resumenDelDia(numero, desde)
        if (resumen.isEmpty()) {
            tvResumen.visibility = View.GONE
        } else {
            tvResumen.visibility = View.VISIBLE
            tvResumen.text = resumen
        }

        // horario del recorrido, solo si Envios Extra lo escribio en el texto
        val horario = Filtro.horarioDelTexto(titulo, texto, detalle)
        if (horario == null) {
            tvHorario.visibility = View.GONE
        } else {
            tvHorario.visibility = View.VISIBLE
            tvHorario.text = "Recorrido: $horario"
        }
    }

    private fun conMayuscula(texto: String): String =
        if (texto.isEmpty()) texto
        else texto.substring(0, 1).uppercase(Locale("es", "CL")) + texto.substring(1)

    /**
     * Si el telefono tiene clave, Android no deja saltar a otra app sin
     * desbloquear. Pedimos el desbloqueo y recien ahi abrimos Envios Extra.
     */
    private fun abrirConDesbloqueo() {
        Voz.callar()
        Alarma.detener()
        Aviso.quitar(applicationContext)

        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km != null && km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    abrirEnviosExtra()
                }

                override fun onDismissError() {
                    abrirEnviosExtra()
                }

                override fun onDismissCancelled() {
                    // la persona decidio no desbloquear: no hacemos nada
                }
            })
        } else {
            abrirEnviosExtra()
        }
    }

    private fun abrirEnviosExtra() {
        val intent = packageManager.getLaunchIntentForPackage(Prefs.PAQUETE_ENVIOS_EXTRA)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // si no se puede abrir, al menos cerramos el aviso
            }
        }
        finish()
    }

    private fun silenciar() {
        Voz.callar()
        Alarma.detener()
        Aviso.quitar(applicationContext)
        finish()
    }
}
