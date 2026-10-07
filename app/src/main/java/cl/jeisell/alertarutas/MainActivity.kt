package cl.jeisell.alertarutas

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var tvEstado: TextView
    private lateinit var tvSegundos: TextView
    private lateinit var tvRegistro: TextView
    private lateinit var swAlerta: Switch
    private lateinit var swHorario: Switch
    private lateinit var swSinLimite: Switch
    private lateinit var sbSegundos: SeekBar
    private lateinit var btnDesde: Button
    private lateinit var btnHasta: Button
    private lateinit var togglesDias: List<Pair<Int, ToggleButton>>

    private val handler = Handler(Looper.getMainLooper())
    private val refrescar = object : Runnable {
        override fun run() {
            pintarEstado()
            pintarRegistro()
            handler.postDelayed(this, 3000L)
        }
    }

    private val formato = SimpleDateFormat("dd/MM HH:mm", Locale("es", "CL"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvEstado = findViewById(R.id.tvEstado)
        tvSegundos = findViewById(R.id.tvSegundos)
        tvRegistro = findViewById(R.id.tvRegistro)
        swAlerta = findViewById(R.id.swAlerta)
        swHorario = findViewById(R.id.swHorario)
        swSinLimite = findViewById(R.id.swSinLimite)
        sbSegundos = findViewById(R.id.sbSegundos)
        btnDesde = findViewById(R.id.btnDesde)
        btnHasta = findViewById(R.id.btnHasta)

        togglesDias = listOf(
            Calendar.MONDAY to findViewById<ToggleButton>(R.id.tbLun),
            Calendar.TUESDAY to findViewById<ToggleButton>(R.id.tbMar),
            Calendar.WEDNESDAY to findViewById<ToggleButton>(R.id.tbMie),
            Calendar.THURSDAY to findViewById<ToggleButton>(R.id.tbJue),
            Calendar.FRIDAY to findViewById<ToggleButton>(R.id.tbVie),
            Calendar.SATURDAY to findViewById<ToggleButton>(R.id.tbSab),
            Calendar.SUNDAY to findViewById<ToggleButton>(R.id.tbDom)
        )

        pedirPermisoDeAvisos()
        configurarAlarma()
        configurarHorario()

        findViewById<Button>(R.id.btnPermiso).setOnClickListener { abrirAjustesDeAcceso() }
        findViewById<Button>(R.id.btnPantallaCompleta).setOnClickListener {
            abrirAjustesDePantallaCompleta()
        }
        findViewById<Button>(R.id.btnCompartir).setOnClickListener { compartirRegistro() }
        findViewById<Button>(R.id.btnBorrar).setOnClickListener {
            Prefs.borrarRegistro(this)
            pintarRegistro()
            Toast.makeText(this, "Registro borrado", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnRespaldo).setOnClickListener { respaldarAhora() }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refrescar)
        handler.post(refrescar)
        // respaldo automatico: como maximo un archivo por dia, sin molestar
        Respaldo.exportarSiCorresponde(this)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refrescar)
    }

    // ------------------------------------------------------------ configurar

    private fun configurarAlarma() {
        swAlerta.isChecked = Prefs.alertaActiva(this)
        swAlerta.setOnCheckedChangeListener { _, marcado ->
            Prefs.setAlertaActiva(this, marcado)
            if (!marcado) {
                Alarma.detener()
                Aviso.quitar(this)
            }
        }

        swSinLimite.isChecked = Prefs.sinLimite(this)
        swSinLimite.setOnCheckedChangeListener { _, marcado ->
            Prefs.setSinLimite(this, marcado)
            pintarSegundos()
        }

        sbSegundos.progress = (Prefs.segundosAlarma(this) - 5).coerceIn(0, 55)
        pintarSegundos()
        sbSegundos.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, valor: Int, deUsuario: Boolean) {
                Prefs.setSegundosAlarma(this@MainActivity, valor + 5)
                pintarSegundos()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        findViewById<Button>(R.id.btnProbar).setOnClickListener {
            Alarma.sonar(this, Prefs.duracionAlarma(this))
        }
        findViewById<Button>(R.id.btnDetener).setOnClickListener {
            Alarma.detener()
            Aviso.quitar(this)
        }
    }

    private fun configurarHorario() {
        swHorario.isChecked = Prefs.horarioActivo(this)
        swHorario.setOnCheckedChangeListener { _, marcado ->
            Prefs.setHorarioActivo(this, marcado)
            pintarHorario()
        }

        val guardados = Prefs.dias(this)
        for ((dia, toggle) in togglesDias) {
            toggle.isChecked = guardados.contains(dia)
            toggle.setOnCheckedChangeListener { _, _ -> guardarDias() }
        }

        btnDesde.setOnClickListener { elegirHora(true) }
        btnHasta.setOnClickListener { elegirHora(false) }

        pintarHorario()
    }

    private fun guardarDias() {
        val marcados = HashSet<Int>()
        for ((dia, toggle) in togglesDias) {
            if (toggle.isChecked) marcados.add(dia)
        }
        Prefs.setDias(this, marcados)
    }

    private fun elegirHora(esDesde: Boolean) {
        val actual = if (esDesde) Prefs.desdeMinutos(this) else Prefs.hastaMinutos(this)
        val dialogo = TimePickerDialog(
            this,
            { _, hora, minuto ->
                val total = hora * 60 + minuto
                if (esDesde) Prefs.setDesdeMinutos(this, total)
                else Prefs.setHastaMinutos(this, total)
                pintarHorario()
            },
            actual / 60,
            actual % 60,
            true
        )
        dialogo.show()
    }

    private fun pedirPermisoDeAvisos() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val concedido = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!concedido) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
    }

    // ---------------------------------------------------------------- pintado

    private fun pintarSegundos() {
        val sinLimite = Prefs.sinLimite(this)
        sbSegundos.isEnabled = !sinLimite
        tvSegundos.text = if (sinLimite) {
            "Duracion de la alarma: hasta que la apagues"
        } else {
            "Duracion de la alarma: ${Prefs.segundosAlarma(this)} segundos"
        }
    }

    private fun pintarHorario() {
        val activo = Prefs.horarioActivo(this)
        for ((_, toggle) in togglesDias) {
            toggle.isEnabled = activo
        }
        btnDesde.isEnabled = activo
        btnHasta.isEnabled = activo
        btnDesde.text = "Desde ${hora(Prefs.desdeMinutos(this))}"
        btnHasta.text = "Hasta ${hora(Prefs.hastaMinutos(this))}"
    }

    private fun hora(minutos: Int): String =
        String.format(Locale.US, "%02d:%02d", minutos / 60, minutos % 60)

    private fun pintarEstado() {
        val conPermiso = accesoConcedido()
        val conApp = enviosExtraInstalada()
        tvEstado.text = when {
            !conPermiso ->
                "Falta el permiso: toca el boton y activa \"Alerta Rutas\" en la lista."
            !conApp ->
                "Permiso listo, pero no encuentro la app Envios Extra en este telefono."
            !Prefs.alertaActiva(this) ->
                "Alerta apagada. Enciende el switch para volver a escuchar."
            !Aviso.puedeUsarPantallaCompleta(this) ->
                "Escuchando. Falta permitir el aviso en pantalla completa para que la oferta aparezca sola."
            !Prefs.enHorario(this) ->
                "Escuchando, pero fuera de tu horario: las ofertas se anotan sin alarma."
            else ->
                "Todo listo. Escuchando las notificaciones de Envios Extra."
        }
    }

    private fun pintarRegistro() {
        val ofertas = Prefs.registro(this)
        if (ofertas.isEmpty()) {
            tvRegistro.text = "Todavia no llega ninguna oferta."
            return
        }
        val sb = StringBuilder()
        for (o in ofertas) {
            sb.append(formato.format(Date(o.momento)))
            sb.append("  ")
            if (o.titulo.isNotBlank()) {
                sb.append(o.titulo)
                if (o.texto.isNotBlank()) sb.append(" - ")
            }
            sb.append(o.texto)
            sb.append("\n")
        }
        tvRegistro.text = sb.toString().trimEnd()
    }

    // --------------------------------------------------------------- acciones

    private fun compartirRegistro() {
        val ofertas = Prefs.registro(this)
        if (ofertas.isEmpty()) {
            Toast.makeText(this, "No hay nada que compartir", Toast.LENGTH_SHORT).show()
            return
        }
        val sb = StringBuilder("Ofertas de ruta - Alerta Rutas\n\n")
        for (o in ofertas) {
            sb.append(formato.format(Date(o.momento)))
            sb.append("\t")
            sb.append(o.titulo)
            sb.append("\t")
            sb.append(o.texto)
            sb.append("\n")
        }
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, sb.toString())
        startActivity(Intent.createChooser(intent, "Compartir registro"))
    }

    private fun respaldarAhora() {
        val nombre = Respaldo.exportar(this)
        if (nombre == null) {
            val vacio = Prefs.registro(this).isEmpty()
            val mensaje = if (vacio) {
                "No hay ofertas para respaldar"
            } else {
                "No se pudo guardar el archivo en Descargas"
            }
            Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
            return
        }
        Prefs.setUltimoRespaldo(this, Respaldo.hoy())
        Toast.makeText(this, "Guardado en Descargas: $nombre", Toast.LENGTH_LONG).show()
    }

    private fun abrirAjustesDePantallaCompleta() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Toast.makeText(
                this,
                "Tu version de Android no necesita este permiso",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Abre Ajustes > Aplicaciones > Alerta Rutas > Notificaciones de pantalla completa",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun abrirAjustesDeAcceso() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Abre Ajustes > Notificaciones > Acceso a notificaciones",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ----------------------------------------------------------------- checks

    private fun accesoConcedido(): Boolean {
        val activos = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return activos.contains(packageName)
    }

    private fun enviosExtraInstalada(): Boolean {
        return try {
            packageManager.getPackageInfo(Prefs.PAQUETE_ENVIOS_EXTRA, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
