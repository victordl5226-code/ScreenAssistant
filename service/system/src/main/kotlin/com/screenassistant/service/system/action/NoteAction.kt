package com.screenassistant.service.system.action

import android.content.Context
import com.screenassistant.core.domain.model.SystemCommand
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Notas por voz como archivos de texto plano en el almacenamiento interno.
 * Cada nota es un fichero "{epochMillis}.txt" dentro de filesDir/notas; si el
 * reloj devuelve un timestamp ya usado, se incrementa (nunca se sobrescribe).
 *
 * Lectura (N1, ADR-012): "lee mis notas" → resumen con extracto de la más reciente;
 * "lee la nota de X" → búsqueda por contenido normalizado (substring, ver
 * normalizeLocal) con lectura truncada a READ_CHARS_LIMIT y aviso de longitud.
 *
 * El límite de tamaño se aplica AQUÍ (SystemCommand.MAX_NOTE_CHARS, fuente única):
 * el parser viaja con el texto completo. N-OBS5: el truncado avanza por CODE POINTS
 * (nunca parte un surrogate pair); el aviso refleja si el truncado cortó de verdad
 * (finalText.length < text.length, no text.length > max).
 */
@Singleton
class NoteAction @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notesDirProvider: () -> File = { File(context.filesDir, "notas") },
    private val now: () -> Long = System::currentTimeMillis,
    // N-OBS4: escritura delegada al provider inyectable (los tests inyectan una lambda
    // que lanza IOException para cubrir el catch, y capturan el archivo recibido).
    private val writeNote: (File, String) -> Unit = { f, t -> f.writeText(t, Charsets.UTF_8) }
) {
    companion object {
        const val EXCERPT_LENGTH = 80
        // N1: límite de caracteres leídos por voz en una sola respuesta (paginación diferida).
        private const val READ_CHARS_LIMIT = 400
        // N1: filtro de archivos de nota ({epochMillis}.txt, 13 dígitos). RAW STRING
        // obligatorio: con comillas normales "\d" sería un error de compilación.
        private val NOTE_FILE_REGEX = Regex("""^\d{13}\.txt$""")
    }

    suspend fun saveNote(text: String): String {
        if (text.trim().isEmpty()) return "Error: La nota está vacía."

        val dir = notesDirProvider()
        if (!dir.exists() && !dir.mkdirs()) return "Error: No pude guardar la nota."

        // N-OBS5: truncado por CODE POINTS (un emoji en la posición 1000 ya no se parte).
        val finalText = truncateByCodePoints(text, SystemCommand.MAX_NOTE_CHARS)
        // Aviso SOLO si el truncado cortó de verdad (si no cortó, no se avisa).
        val truncated = finalText.length < text.length
        // Extracto sobre el texto GUARDADO (finalText), no sobre el original.
        val excerpt = excerptOf(finalText)

        return try {
            withContext(Dispatchers.IO) {
                var ts = now()
                while (File(dir, "$ts.txt").exists()) ts++
                writeNote(File(dir, "$ts.txt"), finalText)
            }
            if (truncated) {
                "Éxito: Nota guardada con aviso: tenía ${text.length} caracteres y el máximo es " +
                    "${SystemCommand.MAX_NOTE_CHARS}, así que la corté. Empieza así: «$excerpt»"
            } else {
                "Éxito: Nota guardada. Empieza así: «$excerpt»"
            }
        } catch (e: IOException) {
            "Error: No pude guardar la nota."
        } catch (e: Exception) {
            "Error: No pude guardar la nota."
        }
    }

    /**
     * N1: resumen de todas las notas — extracto de la más reciente (timestamp mayor).
     * Los archivos ajenos al formato {13 dígitos}.txt se ignoran (README, etc.).
     */
    suspend fun readNotesSummary(): String {
        val dir = notesDirProvider()
        return try {
            withContext(Dispatchers.IO) {
                val notes = listNotes(dir)
                when {
                    notes.isEmpty() -> "Error: No tienes notas."
                    notes.size == 1 -> {
                        "Éxito: Tienes 1 nota. Empieza así: «${excerptOf(notes.first().readText(Charsets.UTF_8))}»"
                    }
                    else -> {
                        val excerpt = excerptOf(notes.first().readText(Charsets.UTF_8))
                        "Éxito: Tienes ${notes.size} notas. La más reciente empieza así: «$excerpt»"
                    }
                }
            }
        } catch (e: Exception) {
            "Error: No pude leer las notas."
        }
    }

    /**
     * N1: lee la nota(s) que CONTIENE la query (substring sobre contenido normalizado).
     * 0 coincidencias → error; 1 → contenido completo o truncado a READ_CHARS_LIMIT;
     * N → más reciente con aviso de cuántas coincidencias hay.
     */
    suspend fun readNote(query: String): String {
        val dir = notesDirProvider()
        return try {
            withContext(Dispatchers.IO) {
                val notes = listNotes(dir)
                // Normalización LOCAL (ADR-003): no se reutiliza normalize del parser —
                // core:domain no debe filtrarse a service:system, y aquí la normalización
                // es para MATCH por substring, no para alineación de índices. Falso
                // positivo aceptado por diseño: "pan" matchea "españa" (no usar match de
                // palabra: rompería tildes/ñ, ADR-012).
                val normQuery = normalizeLocal(query)
                val matches = notes.filter { normalizeLocal(it.readText(Charsets.UTF_8)).contains(normQuery) }
                when {
                    matches.isEmpty() -> "Error: No encontré ninguna nota con «$query»."
                    matches.size == 1 -> readOne(matches.first(), query)
                    else -> readOne(matches.first(), query, matches.size)
                }
            }
        } catch (e: Exception) {
            "Error: No pude leer las notas."
        }
    }

    private suspend fun readOne(file: File, query: String, totalMatches: Int = 1): String {
        val content = file.readText(Charsets.UTF_8)
        val chunk = truncateByCodePoints(content, READ_CHARS_LIMIT)
        // Ronda de cierre: la rama "larga" se decide por CODE POINTS percibidos, no por
        // length UTF-16. Una nota de 400 code points con emoji (401+ UTF-16) CABE en 400
        // code points: truncateByCodePoints devuelve el contenido COMPLETO y añadir
        // "..." sería engañoso → lectura completa sin aviso.
        val long = chunk.length < content.length
        return if (totalMatches == 1) {
            if (long) {
                "Éxito: La nota es larga, ${content.length} caracteres; te leo el principio: «$chunk...»"
            } else {
                "Éxito: La nota dice: «$content»"
            }
        } else {
            "Éxito: Encontré $totalMatches notas con «$query»; te leo la más reciente: " +
                (if (long) "«$chunk...»" else "«$content»")
        }
    }

    private fun listNotes(dir: File): List<File> =
        dir.listFiles().orEmpty()
            .filter { NOTE_FILE_REGEX.matches(it.name) }
            // Timestamp mayor = más reciente (nombres {epochMillis}.txt ordenables).
            .sortedByDescending { it.name }

    /** Extracto del texto (80 code points máx. + "..." si aplica). */
    private fun excerptOf(text: String): String =
        if (text.length <= EXCERPT_LENGTH) text
        else truncateByCodePoints(text, EXCERPT_LENGTH) + "..."

    /**
     * N-OBS5: truncado por CODE POINTS (UTF-16 safe) — nunca parte un surrogate pair.
     * Si text.length (UTF-16) <= max, se devuelve intacto.
     *
     * QA (ronda de cierre): el guard `idx < text.length` del bucle evita leer más allá
     * del final. Con una nota 100% emoji de 201-399 emojis (code points < max pero
     * UTF-16 > max), el guard inicial entra al bucle y sin este guard `codePointAt(idx)`
     * se llamaba con idx == length (StringIndexOutOfBoundsException) → el catch genérico
     * de readNote/saveNote devolvía "Error: No pude leer/guardar la nota.". Ahora el
     * bucle se detiene al consumir el último code point: no hay nada más que truncar
     * (el texto se devuelve íntegro). Aplica a los 3 call-sites (guardado/excerpt/lectura).
     */
    private fun truncateByCodePoints(text: String, max: Int): String {
        if (text.length <= max) return text
        var idx = 0
        var count = 0
        while (count < max && idx < text.length) {
            idx += Character.charCount(text.codePointAt(idx))
            count++
        }
        return text.substring(0, idx)
    }

    /**
     * lowercase + sin tildes + ñ→n + puntuación→espacio para el MATCH de readNote
     * (copiada localmente, ver ADR-003: se acepta la duplicación hasta que aparezca
     * un tercer consumidor, entonces se extrae a un lugar compartido).
     *
     * Replica EXACTAMENTE la cadena de normalize del parser (SystemCommandParser):
     * incluye 'ü'→'u' ("pingüino" hablado → "pinguino" encuentra la nota "pingüino")
     * y el paso puntuación→espacio ("pan, leche" matchea "pan leche"). El ':' queda
     * EXCLUIDO igual que en el parser (consistencia con la rama alarma).
     *
     * EXTENSIÓN LOCAL: colapso de espacios + trim. El paso puntuación→espacio genera
     * dobles espacios ("pan, leche" → "pan  leche"); el parser NO colapsa (su contrato
     * es longitud invariante para índices), pero aquí es MATCH por substring y el
     * doble espacio rompería la coincidencia → se colapsa (sin significado, solo
     * artefacto del dictado).
     */
    private fun normalizeLocal(text: String): String =
        text.lowercase()
            .replace('á', 'a').replace('é', 'e').replace('í', 'i')
            .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')
            .replace('ñ', 'n')
            .replace('¿', ' ').replace('¡', ' ').replace('?', ' ').replace('!', ' ')
            .replace('.', ' ').replace(',', ' ').replace(';', ' ')
            .trim()
            .replace(Regex("""\s+"""), " ")
}
