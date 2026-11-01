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
    val estado: String = "",
    /** Texto adicional que trae la notificacion y no se ve en la barra. */
    val extra: String = "",
    /** Identificador de la notificacion, para poder cerrarla despues. */
    val clave: String = "",
    /** Cuanto estuvo viva la oferta en la bandeja. -1 si todavia no se sabe. */
    val duroMs: Long = -1L,
    /** Por que desaparecio: la retiro Mercado Libre, la abriste, etc. */
    val fin: String = ""
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
    const val ESTADO_NO_ES_OFERTA = "no_es_oferta"

    private const val ARCHIVO = "alerta_rutas"
    private const val KEY_ACTIVA = "alerta_activa"
    private const val KEY_SEGUNDOS = "segundos_alarma"
    private const val KEY_SIN_LIMITE = "sin_limite"
    private const val KEY_ULTIMO_RESPALDO = "ultimo_respaldo"
    private const val KEY_SOLO_OFERTAS = "solo_ofertas"
    private const val KEY_PALABRAS_OFERTA = "palabras_oferta"
    private const val KEY_PALABRAS_IGNORAR = "palabras_ignorar"
    private const val KEY_CONTADOR = "contador_ofertas"
    private const val KEY_CONTADOR_FECHA = "contador_fecha"
    private const val KEY_ULTIMA_OFERTA = "ultima_oferta"
    private const val KEY_VOZ = "voz_activa"
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

    /** True si ademas de la alarma hay que decir la oferta en voz alta. */
    fun vozActiva(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_VOZ, false)

    fun setVozActiva(ctx: Context, valor: Boolean) {
        sp(ctx).edit().putBoolean(KEY_VOZ, valor).apply()
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

    // ---------------------------------------------------------------- filtro

    /** Si esta activo, la alarma suena solo cuando el aviso parece oferta. */
    fun soloOfertas(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_SOLO_OFERTAS, true)

    fun setSoloOfertas(ctx: Context, valor: Boolean) {
        sp(ctx).edit().putBoolean(KEY_SOLO_OFERTAS, valor).apply()
    }

    fun palabrasOferta(ctx: Context): List<String> =
        listaGuardada(ctx, KEY_PALABRAS_OFERTA) ?: Filtro.PALABRAS_OFERTA

    fun setPalabrasOferta(ctx: Context, texto: String) {
        sp(ctx).edit().putString(KEY_PALABRAS_OFERTA, texto).apply()
    }

    fun palabrasIgnorar(ctx: Context): List<String> =
        listaGuardada(ctx, KEY_PALABRAS_IGNORAR) ?: Filtro.PALABRAS_IGNORAR

    fun setPalabrasIgnorar(ctx: Context, texto: String) {
        sp(ctx).edit().putString(KEY_PALABRAS_IGNORAR, texto).apply()
    }

    /** Texto editable que se muestra en la pantalla de ajustes. */
    fun textoPalabrasOferta(ctx: Context): String =
        sp(ctx).getString(KEY_PALABRAS_OFERTA, null) ?: Filtro.PALABRAS_OFERTA.joinToString(", ")

    fun textoPalabrasIgnorar(ctx: Context): String =
        sp(ctx).getString(KEY_PALABRAS_IGNORAR, null) ?: Filtro.PALABRAS_IGNORAR.joinToString(", ")

    fun restaurarPalabras(ctx: Context) {
        sp(ctx).edit()
            .remove(KEY_PALABRAS_OFERTA)
            .remove(KEY_PALABRAS_IGNORAR)
            .apply()
    }

    private fun listaGuardada(ctx: Context, clave: String): List<String>? {
        val crudo = sp(ctx).getString(clave, null) ?: return null
        val lista = crudo.split(",", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return if (lista.isEmpty()) emptyList() else lista
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
        estado: String = "",
        extra: String = "",
        /**
         * Momento en que la app atendio el aviso. Se recibe de afuera para que
         * la medicion de reaccion no incluya lo que tarda este guardado.
         */
        momento: Long = System.currentTimeMillis(),
        clave: String = ""
    ) {
        val anteriores = registro(ctx)
        val nuevo = JSONArray()
        val actual = JSONObject()
        actual.put("t", momento)
        actual.put("titulo", titulo)
        actual.put("texto", texto)
        actual.put("r", recibida)
        actual.put("e", estado)
        actual.put("x", extra)
        actual.put("k", clave)
        actual.put("d", -1L)
        actual.put("f", "")
        nuevo.put(actual)
        for (oferta in anteriores) {
            if (nuevo.length() >= MAX_REGISTROS) break
            nuevo.put(aJson(oferta))
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
                        estado = o.optString("e", ""),
                        extra = o.optString("x", ""),
                        clave = o.optString("k", ""),
                        duroMs = o.optLong("d", -1L),
                        fin = o.optString("f", "")
                    )
                )
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return lista
    }

    private fun aJson(oferta: Oferta): JSONObject {
        val o = JSONObject()
        o.put("t", oferta.momento)
        o.put("titulo", oferta.titulo)
        o.put("texto", oferta.texto)
        o.put("r", oferta.recibida)
        o.put("e", oferta.estado)
        o.put("x", oferta.extra)
        o.put("k", oferta.clave)
        o.put("d", oferta.duroMs)
        o.put("f", oferta.fin)
        return o
    }

    /**
     * Anota cuanto duro una oferta y por que desaparecio de la bandeja.
     * Solo toca la anotacion mas reciente que tenga esa clave y siga abierta.
     */
    fun cerrarOferta(ctx: Context, clave: String, duroMs: Long, fin: String) {
        if (clave.isBlank()) return
        val ofertas = registro(ctx)
        var yaCerrada = false
        val nuevo = JSONArray()
        for (oferta in ofertas) {
            val toca = !yaCerrada && oferta.clave == clave && oferta.duroMs < 0
            if (toca) {
                yaCerrada = true
                nuevo.put(aJson(oferta.copy(duroMs = duroMs, fin = fin)))
            } else {
                nuevo.put(aJson(oferta))
            }
        }
        if (!yaCerrada) return
        sp(ctx).edit().putString(KEY_LOG, nuevo.toString()).apply()
    }

    // --------------------------------------------------------------- contador

    /**
     * Cuenta la oferta que acaba de llegar y devuelve dos datos baratos de
     * calcular: cuantas van hoy, y cuantos milisegundos pasaron desde la
     * anterior (-1 si es la primera).
     *
     * Se guarda aparte del registro a proposito: esto corre antes de que suene
     * la alarma, asi que tiene que ser liviano.
     */
    fun contarOferta(ctx: Context, momento: Long, hoy: String): Pair<Int, Long> {
        val prefs = sp(ctx)
        val fechaGuardada = prefs.getString(KEY_CONTADOR_FECHA, "") ?: ""
        val llevaba = if (fechaGuardada == hoy) prefs.getInt(KEY_CONTADOR, 0) else 0
        val anterior = prefs.getLong(KEY_ULTIMA_OFERTA, 0L)
        val desde = if (anterior <= 0L || momento < anterior) -1L else momento - anterior

        prefs.edit()
            .putString(KEY_CONTADOR_FECHA, hoy)
            .putInt(KEY_CONTADOR, llevaba + 1)
            .putLong(KEY_ULTIMA_OFERTA, momento)
            .apply()

        return Pair(llevaba + 1, desde)
    }

    fun ofertasDeHoy(ctx: Context, hoy: String): Int {
        val prefs = sp(ctx)
        val fechaGuardada = prefs.getString(KEY_CONTADOR_FECHA, "") ?: ""
        return if (fechaGuardada == hoy) prefs.getInt(KEY_CONTADOR, 0) else 0
    }

    fun borrarRegistro(ctx: Context) {
        sp(ctx).edit().remove(KEY_LOG).apply()
    }
}
