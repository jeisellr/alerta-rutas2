package cl.jeisell.alertarutas

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Oferta de ruta detectada en una notificacion de Envios Extra.
 *
 * [recibida] es la hora en que Envios Extra publico la notificacion y
 * [momento] cuando esta app la proceso: la resta de las dos es la demora
 * real de la app, el dato que sirve para saber si esta llegando tarde.
 */
data class Oferta(
    val momento: Long,
    val titulo: String,
    val texto: String,
    val recibida: Long = 0L,
    val estado: String = ""
) {
    /** Milisegundos que tardo la app en reaccionar. -1 si no se pudo medir. */
    val demoraMs: Long
        get() = if (recibida <= 0L || momento < recibida) -1L else momento - recibida
}

/**
 * Guarda la configuracion y el registro de ofertas en SharedPreferences.
 * No se usa base de datos a proposito: el volumen de datos es minimo.
 */
object Prefs {

    const val PAQUETE_ENVIOS_EXTRA = "com.mercadoenvios.crowdsourcing"

    /** Motivos posibles de cada oferta anotada. */
    const val ESTADO_SONO = "sono"
    const val ESTADO_FUERA_HORARIO = "fuera_horario"
    const val ESTADO_ALERTA_APAGADA = "alerta_apagada"

    private const val ARCHIVO = "alerta_rutas"
    private const val KEY_ACTIVA = "alerta_activa"
    private const val KEY_SEGUNDOS = "segundos_alarma"
    private const val KEY_SIN_LIMITE = "sin_limite"
    private const val KEY_ULTIMO_RESPALDO = "ultimo_respaldo"
    private const val KEY_LOG = "registro"
    private const val KEY_HORARIO = "horario_activo"
    private const val KEY_DIAS = "dias"
    private const val KEY_DESDE = "desde_minutos"
    private const val KEY_HASTA = "hasta_minutos"
    private const val MAX_REGISTROS = 400

    /** Dias de la semana en orden chileno: lunes primero, domingo al final. */
    val DIAS_SEMANA = listOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY,
        Calendar.SUNDAY
    )

    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- alarma

    fun alertaActiva(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_ACTIVA, true)

    fun setAlertaActiva(ctx: Context, valor: Boolean) {
        sp(ctx).edit().putBoolean(KEY_ACTIVA, valor).apply()
    }

    fun segundosAlarma(ctx: Context): Int = sp(ctx).getInt(KEY_SEGUNDOS, 20)

    fun setSegundosAlarma(ctx: Context, valor: Int) {
        sp(ctx).edit().putInt(KEY_SEGUNDOS, valor).apply()
    }

    /** True si la alarma debe sonar hasta que la persona la silencie. */
    fun sinLimite(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_SIN_LIMITE, false)

    fun setSinLimite(ctx: Context, valor: Boolean) {
        sp(ctx).edit().putBoolean(KEY_SIN_LIMITE, valor).apply()
    }

    /**
     * Duracion que hay que pasarle a [Alarma.sonar].
     * 0 significa "sin corte": Alarma aplica su propio tope de seguridad.
     */
    fun duracionAlarma(ctx: Context): Int =
        if (sinLimite(ctx)) 0 else segundosAlarma(ctx)

    // --------------------------------------------------------------- respaldo

    /** Fecha del ultimo respaldo automatico, con formato aaaa-mm-dd. */
    fun ultimoRespaldo(ctx: Context): String =
        sp(ctx).getString(KEY_ULTIMO_RESPALDO, "") ?: ""

    fun setUltimoRespaldo(ctx: Context, fecha: String) {
        sp(ctx).edit().putString(KEY_ULTIMO_RESPALDO, fecha).apply()
    }

    // --------------------------------------------------------------- horario

    fun horarioActivo(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_HORARIO, false)

    fun setHorarioActivo(ctx: Context, valor: Boolean) {
        sp(ctx).edit().putBoolean(KEY_HORARIO, valor).apply()
    }

    /** Dias marcados, como constantes Calendar.DAY_OF_WEEK. Por defecto todos. */
    fun dias(ctx: Context): Set<Int> {
        val crudo = sp(ctx).getString(KEY_DIAS, null) ?: return DIAS_SEMANA.toSet()
        if (crudo.isBlank()) return emptySet()
        val set = HashSet<Int>()
        for (parte in crudo.split(",")) {
            val n = parte.trim().toIntOrNull() ?: continue
            set.add(n)
        }
        return set
    }

    fun setDias(ctx: Context, valor: Set<Int>) {
        sp(ctx).edit().putString(KEY_DIAS, valor.joinToString(",")).apply()
    }

    fun desdeMinutos(ctx: Context): Int = sp(ctx).getInt(KEY_DESDE, 8 * 60)

    fun setDesdeMinutos(ctx: Context, valor: Int) {
        sp(ctx).edit().putInt(KEY_DESDE, valor.coerceIn(0, 24 * 60 - 1)).apply()
    }

    fun hastaMinutos(ctx: Context): Int = sp(ctx).getInt(KEY_HASTA, 22 * 60)

    fun setHastaMinutos(ctx: Context, valor: Int) {
        sp(ctx).edit().putInt(KEY_HASTA, valor.coerceIn(0, 24 * 60 - 1)).apply()
    }

    /**
     * True si en este momento corresponde hacer sonar la alarma.
     * Si el horario esta desactivado, siempre es true.
     * Soporta turnos que cruzan la medianoche (por ejemplo 18:00 a 02:00):
     * en ese caso las horas de la madrugada cuentan como del dia anterior.
     */
    fun enHorario(ctx: Context, calendario: Calendar = Calendar.getInstance()): Boolean {
        if (!horarioActivo(ctx)) return true

        val marcados = dias(ctx)
        if (marcados.isEmpty()) return false

        val dia = calendario.get(Calendar.DAY_OF_WEEK)
        val ahora = calendario.get(Calendar.HOUR_OF_DAY) * 60 + calendario.get(Calendar.MINUTE)
        val desde = desdeMinutos(ctx)
        val hasta = hastaMinutos(ctx)

        // mismo valor en las dos horas = todo el dia
        if (desde == hasta) return marcados.contains(dia)

        return if (desde < hasta) {
            marcados.contains(dia) && ahora >= desde && ahora < hasta
        } else {
            when {
                ahora >= desde -> marcados.contains(dia)
                ahora < hasta -> marcados.contains(diaAnterior(dia))
                else -> false
            }
        }
    }

    /**
     * Configuracion peligrosa: el horario esta activo pero no hay ningun dia
     * marcado, asi que la alarma no va a sonar nunca.
     */
    fun horarioSinDias(ctx: Context): Boolean =
        horarioActivo(ctx) && dias(ctx).isEmpty()

    private fun diaAnterior(dia: Int): Int =
        if (dia == Calendar.SUNDAY) Calendar.SATURDAY else dia - 1

    // --------------------------------------------------------------- registro

    fun registrar(
        ctx: Context,
        titulo: String,
        texto: String,
        recibida: Long = 0L,
        estado: String = ""
    ) {
        val anteriores = registro(ctx)
        val nuevo = JSONArray()
        val actual = JSONObject()
        actual.put("t", System.currentTimeMillis())
        actual.put("titulo", titulo)
        actual.put("texto", texto)
        actual.put("r", recibida)
        actual.put("e", estado)
        nuevo.put(actual)
        for (oferta in anteriores) {
            if (nuevo.length() >= MAX_REGISTROS) break
            val o = JSONObject()
            o.put("t", oferta.momento)
            o.put("titulo", oferta.titulo)
            o.put("texto", oferta.texto)
            o.put("r", oferta.recibida)
            o.put("e", oferta.estado)
            nuevo.put(o)
        }
        sp(ctx).edit().putString(KEY_LOG, nuevo.toString()).apply()
    }

    /** Ofertas guardadas, la mas reciente primero. */
    fun registro(ctx: Context): List<Oferta> {
        val crudo = sp(ctx).getString(KEY_LOG, null) ?: return emptyList()
        val lista = ArrayList<Oferta>()
        try {
            val arr = JSONArray(crudo)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                lista.add(
                    Oferta(
                        momento = o.optLong("t", 0L),
                        titulo = o.optString("titulo", ""),
                        texto = o.optString("texto", ""),
                        recibida = o.optLong("r", 0L),
                        estado = o.optString("e", "")
                    )
                )
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return lista
    }

    fun borrarRegistro(ctx: Context) {
        sp(ctx).edit().remove(KEY_LOG).apply()
    }
}
