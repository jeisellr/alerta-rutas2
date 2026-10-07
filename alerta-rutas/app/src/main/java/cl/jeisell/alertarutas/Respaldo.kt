package cl.jeisell.alertarutas

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Guarda el registro de ofertas como archivo CSV en la carpeta Descargas,
 * para que no se pierda si se cambia o se reinstala el telefono.
 *
 * Usa MediaStore, asi que no necesita permisos de almacenamiento.
 * El archivo se abre directo en Excel o Google Sheets.
 */
object Respaldo {

    /** Marca de orden de bytes, para que Excel muestre bien los acentos. */
    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    private val FECHA_ARCHIVO = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val FECHA_FILA = SimpleDateFormat("dd-MM-yyyy", Locale.US)
    private val HORA_FILA = SimpleDateFormat("HH:mm", Locale.US)

    fun hoy(): String = FECHA_ARCHIVO.format(Date())

    fun nombreArchivo(fecha: String): String = "alerta-rutas-$fecha.csv"

    /**
     * Arma el contenido del CSV. Separador punto y coma, que es lo que espera
     * Excel en Chile.
     */
    fun armarCsv(ofertas: List<Oferta>): String {
        val sb = StringBuilder("Fecha;Hora;Reaccion ms;Alarma;Titulo;Texto\r\n")
        for (o in ofertas) {
            val momento = Date(o.momento)
            sb.append(campo(FECHA_FILA.format(momento))).append(';')
            sb.append(campo(HORA_FILA.format(momento))).append(';')
            sb.append(campo(if (o.demoraMs < 0) "" else o.demoraMs.toString())).append(';')
            sb.append(campo(descripcionEstado(o.estado))).append(';')
            sb.append(campo(o.titulo)).append(';')
            sb.append(campo(o.texto)).append("\r\n")
        }
        return sb.toString()
    }

    private fun descripcionEstado(estado: String): String = when (estado) {
        Prefs.ESTADO_SONO -> "sono"
        Prefs.ESTADO_FUERA_HORARIO -> "sin alarma: fuera de horario"
        Prefs.ESTADO_ALERTA_APAGADA -> "sin alarma: alerta apagada"
        else -> ""
    }

    /**
     * Escribe el respaldo en Descargas.
     * Devuelve el nombre del archivo, o null si no habia nada que guardar
     * o si el sistema no dejo escribir.
     */
    fun exportar(ctx: Context): String? {
        val ofertas = Prefs.registro(ctx)
        if (ofertas.isEmpty()) return null

        val nombre = nombreArchivo(hoy())
        val valores = ContentValues()
        valores.put(MediaStore.MediaColumns.DISPLAY_NAME, nombre)
        valores.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
        valores.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)

        return try {
            val uri = ctx.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                valores
            ) ?: return null

            val salida = ctx.contentResolver.openOutputStream(uri) ?: return null
            salida.use { flujo ->
                flujo.write(BOM)
                flujo.write(armarCsv(ofertas).toByteArray(Charsets.UTF_8))
            }
            nombre
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Respaldo automatico: deja como maximo un archivo por dia.
     * Devuelve el nombre si guardo algo, o null si hoy ya estaba respaldado.
     */
    fun exportarSiCorresponde(ctx: Context): String? {
        val fecha = hoy()
        if (Prefs.ultimoRespaldo(ctx) == fecha) return null
        val nombre = exportar(ctx) ?: return null
        Prefs.setUltimoRespaldo(ctx, fecha)
        return nombre
    }

    private fun campo(valor: String): String {
        val limpio = valor
            .replace("\"", "\"\"")
            .replace("\n", " ")
            .replace("\r", " ")
        return "\"$limpio\""
    }
}
