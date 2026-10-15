package cl.jeisell.alertarutas

import java.text.Normalizer
import java.util.Locale

/**
 * Decide si una notificacion de Envios Extra es realmente una oferta de ruta,
 * y rescata el horario del recorrido cuando viene escrito en el texto.
 *
 * Las listas de palabras son editables desde la app: si se cuela un aviso que
 * no es oferta, basta agregar una palabra suya a la lista de ignorados, sin
 * tener que volver a compilar.
 */
object Filtro {

    /**
     * Frases que identifican una oferta de ruta de verdad.
     *
     * Se usan frases y no palabras sueltas porque Envios Extra escribe
     * "recorridos" en avisos que no son ofertas: los del desafio, el de la
     * ubicacion en segundo plano y el de las ganancias acreditadas. Buscar
     * solo la palabra "recorrido" hacia sonar la alarma con todos esos.
     */
    val PALABRAS_OFERTA = listOf(
        "recorridos disponibles",
        "recorrido disponible",
        "nuevos recorridos",
        "ofertas disponibles",
        "nueva oferta"
    )

    /** Si aparece alguna de estas, el aviso NO es oferta. Mandan sobre las de arriba. */
    val PALABRAS_IGNORAR = listOf(
        "llegaste al domicilio",
        "usando tu ubicacion",
        "desafio",
        "ganaste",
        "ganancias",
        "muestra tu qr",
        "espera tu",
        "felicitaciones",
        "encuesta",
        "calific",
        "en curso",
        "recordatorio"
    )

    private val MESES = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "setiembre", "octubre",
        "noviembre", "diciembre"
    )

    private val REGEX_RANGO = Regex(
        """\b([01]?\d|2[0-3]):([0-5]\d)\s*(?:a|-|–|hasta)\s*([01]?\d|2[0-3]):([0-5]\d)"""
    )
    private val REGEX_HORA = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""")
    private val REGEX_FECHA = Regex("""\b([0-3]?\d)[/-]([01]?\d)(?:[/-](\d{2,4}))?\b""")
    private val REGEX_DIA_MES = Regex("""\b([0-3]?\d)\s+de\s+(${MESES.joinToString("|")})\b""")
    // "pasado manana" va primero: si no, se quedaria solo con "manana".
    // El numero opcional del final atrapa cosas como "sabado 17".
    private val REGEX_DIA_SEMANA = Regex(
        """\b(pasado manana|manana|hoy|lunes|martes|miercoles|jueves|viernes|sabado|domingo)""" +
            """(?:\s+([0-3]?\d)(?!\s*[:.]))?\b"""
    )

    /** Pasa a minusculas y saca los acentos, para comparar sin sorpresas. */
    fun normalizar(texto: String): String {
        val sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return sinAcentos.lowercase(Locale.US)
    }

    /**
     * True si el aviso parece una oferta de ruta.
     * Las palabras a ignorar tienen prioridad: si aparece una, no es oferta
     * aunque tambien aparezca una palabra de oferta.
     */
    fun esOferta(
        titulo: String,
        texto: String,
        palabrasOferta: List<String> = PALABRAS_OFERTA,
        palabrasIgnorar: List<String> = PALABRAS_IGNORAR
    ): Boolean {
        val contenido = normalizar("$titulo $texto")

        for (palabra in palabrasIgnorar) {
            val limpia = normalizar(palabra).trim()
            if (limpia.isNotEmpty() && contenido.contains(limpia)) return false
        }

        val buscadas = palabrasOferta.map { normalizar(it).trim() }.filter { it.isNotEmpty() }
        if (buscadas.isEmpty()) return true

        for (palabra in buscadas) {
            if (contenido.contains(palabra)) return true
        }
        return false
    }

    /**
     * Rescata el horario del recorrido si viene en el texto, por ejemplo
     * "manana 09:00 a 13:00". Devuelve null cuando no hay nada reconocible,
     * que es lo habitual: Envios Extra no suele incluirlo.
     */
    fun horarioDelTexto(titulo: String, texto: String, extra: String = ""): String? {
        val contenido = normalizar("$titulo $texto $extra")

        val dia = REGEX_DIA_SEMANA.find(contenido)?.value
            ?: REGEX_DIA_MES.find(contenido)?.value
            ?: REGEX_FECHA.find(contenido)?.value

        val rango = REGEX_RANGO.find(contenido)
        val hora = if (rango != null) {
            "${rango.groupValues[1].padStart(2, '0')}:${rango.groupValues[2]}" +
                " a ${rango.groupValues[3].padStart(2, '0')}:${rango.groupValues[4]}"
        } else {
            REGEX_HORA.find(contenido)?.value
        }

        if (dia == null && hora == null) return null

        val partes = ArrayList<String>()
        if (dia != null) partes.add(conMayuscula(dia))
        if (hora != null) partes.add(hora)
        return partes.joinToString(" ")
    }

    private fun conMayuscula(texto: String): String =
        if (texto.isEmpty()) texto
        else texto.substring(0, 1).uppercase(Locale.US) + texto.substring(1)
}
