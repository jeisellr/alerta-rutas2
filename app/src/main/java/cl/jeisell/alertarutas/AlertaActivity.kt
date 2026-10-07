package cl.jeisell.alertarutas

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // encender la pantalla y aparecer aunque este bloqueada
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_alerta)

        tvTitulo = findViewById(R.id.tvAlertaTitulo)
        tvTexto = findViewById(R.id.tvAlertaTexto)

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
        tvTitulo.text = if (titulo.isBlank()) "Oferta de ruta" else titulo
        tvTexto.text = if (texto.isBlank()) "Toca para abrir Envios Extra" else texto
    }

    /**
     * Si el telefono tiene clave, Android no deja saltar a otra app sin
     * desbloquear. Pedimos el desbloqueo y recien ahi abrimos Envios Extra.
     */
    private fun abrirConDesbloqueo() {
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
        Alarma.detener()
        Aviso.quitar(applicationContext)
        finish()
    }
}
