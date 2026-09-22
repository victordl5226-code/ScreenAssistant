package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.model.ActionResult
import com.screenassistant.core.domain.model.AssistantMode
import com.screenassistant.core.domain.model.CalculatorOperator
import com.screenassistant.core.domain.model.CommandMarkers
import com.screenassistant.core.domain.model.SystemCommand
import com.screenassistant.core.domain.usecase.math.MathExpressionNormalizer

/**
 * Parser de comandos directos por voz.
 *
 * Decide sobre `trimmed` (lowercase + sin tildes + ñ→n + trim COMPLETO — O6) y
 * extrae el argumento sobre el texto ORIGINAL preservando el case (la normalización
 * es longitud invariante: los índices coinciden). El trim alinea los espacios de
 * borde entre trimmed y original.trim(); los ¿/¡ residuales del original los absorbe
 * la limpieza de bordes del argumento (M2) y cleanNoteText (rama 0) — ADR-010.
 *
 * Marcadores: CommandMarkers.HELP/REPEAT se devuelven sin pasar por el handler.
 * Orden de ramas = precedencia (ver tabla interna):
 *   notas crear (0), notas leer (0b, N1), ayuda, repite, temporizador, llama al,
 *   llama a, volumen, idioma, ajustes, abre, navega, busca, recuerda,
 *   alarma (con hora hablada), null→Gemini.
 */
open class SystemCommandParser(
    private val systemAction: SystemAction
) {
    val assistantMode = systemAction.assistantMode

    // H4: '.' admitido en la clase de separadores ("600.123.456"). El '.' llega aquí
    // porque la extracción usa el ORIGINAL (normalize convierte '.' en ' ' solo para
    // el dispatch sobre trimmed).
    private val phoneRegex = Regex("""\+?\d[\d\s\-.]*""")
    // M4: alternación de unidades compartida por los regex de duración (evita deriva).
    private val UNIT_PATTERN = "horas?|minutos?|segundos?"
    // O5: unidades de duración. Se suman TODAS las ocurrencias (antes solo el primer match).
    // Los segundos contribuyen 0 minutos y solo activan el ceil (TMP-num: "45 segundos" → 1).
    private val durationUnitRegex = Regex("""(\d+)\s*($UNIT_PATTERN)""")
    // TMP-num: unidades con cantidad HABLADA ("una hora", "veinte y cinco minutos",
    // "cuarenta y cinco segundos"). Grupos de captura: 1=token1, 2=unitToken 1-9
    // (SpokenNumber.unitTokens), 3=unidad — validado igual que en horas (ADR-TMP-7).
    private val durationSpokenUnitRegex = Regex(
        """(${SpokenNumber.quantityTokens().joinToString("|")})(?:\s+y\s+(${SpokenNumber.unitTokens().joinToString("|")}))?\s+($UNIT_PATTERN)"""
    )
    // TMP-num: fracción con unidad propia ("media hora", "cuarto de hora" → +30/+15).
    private val durationSpokenFractionUnitRegex = Regex("""(media|cuarto)\s+(?:de\s+)?horas?""")
    // O5+TMP-num: fracción de hora compuesta ligada a horas ("1 hora y media" → +30,
    // "una hora y cuarto" → +15). Unificado: cantidad numérica O hablada.
    // Lookahead anti-doble-conteo: "1 hora y media hora" NO debe sumar +30 por la fracción
    // compuesta (la suma la hace durationSpokenFractionUnitRegex) → 60+30=90, no 120.
    private val durationFractionRegex = Regex(
        """(?:(?:\d+)|(?:${SpokenNumber.quantityTokens().joinToString("|")}))\s+horas?\s+y\s+(media|cuarto)(?!\s+(?:de\s+)?horas?)"""
    )
    // TMP-2: dígito "desnudo" tras "X horas/minutos y N" sin unidad ("1 hora y 30" → +30).
    // El lookahead negativo bloquea el doble conteo: (a) cuando N SÍ tiene unidad
    // ("1 hora y 30 minutos" → el 30 ya suma por durationUnitRegex) y (b) cuando el \d+
    // retrocede a un prefijo del número ("30 segundos" no debe capturar "y 3"). ADR-TMP-5.
    private val durationBareRegex = Regex(
        """(?:(?:\d+)|(?:${SpokenNumber.quantityTokens().joinToString("|")}))\s*(horas?|minutos?)\s+y\s+(\d+)(?!\s*(?:\d|(?:$UNIT_PATTERN)))"""
    )
    // TMP-num: "X minutos y media/cuarto" residual (ambiguo: no se liga a horas) →
    // rechazo completo a Gemini ("3 minutos y medio"). El lookahead salva
    // "30 minutos y media hora" (la fracción SÍ está ligada a hora → no residual).
    private val minuteFractionResidualRegex = Regex(
        """(?:\d+|${SpokenNumber.quantityTokens().joinToString("|")})\s+minutos?\s+y\s+(?:media|medio|cuarto)(?!\s+horas?)"""
    )
    // B1: decenas NO soportadas (>59) con compuesto "decena y X" + unidad → rechazo
    // completo (B3/ADR-TMP-6). Sin esto, durationSpokenUnitRegex salta la decena y
    // captura el match parcial "cinco minutos" → SetTimer(5) silencioso.
    private val unsupportedTensRegex = Regex(
        """\b(?:sesenta|setenta|ochenta|noventa|cien|ciento)\s+y\s+(?:\d+|[a-z]+)\s+(?:$UNIT_PATTERN)"""
    )
    // B1: compuesto inválido con decena SOPORTADA + token2 fuera de 1-9 ("veinte y diez
    // minutos") → rechazo completo. El lookahead negativo excluye el token2 válido
    // (unitTokens 1-9 palabra o dígito de una cifra) para NO romper "veinte y cinco
    // minutos"→25. Sin lookbehind (rompería "una hora y cinco minutos"→65).
    private val invalidCompoundRegex = Regex(
        """\b(?:veinte|treinta|cuarenta|cincuenta)\s+y\s+(?!(?:${SpokenNumber.unitTokens().joinToString("|")})|(?:[1-9](?![0-9])))(?:\d+|[a-z]+)\s+(?:$UNIT_PATTERN)"""
    )

    // O4: órdenes de cancelación de alarmas (rama 11b, justo ANTES de la rama 12).
    private val cancelAlarmRegex = Regex(
        """^(cancela|cancelar|quita|quitar|elimina|eliminar)\s+(la\s+alarma|las\s+alarmas|mi\s+alarma|mis\s+alarmas)"""
    )

    /**
     * Frases de petición de ayuda (rama 1). Se matchean con límite posterior de
     * palabra o ':' (espacio, ':' o fin de cadena — H5) para evitar falsos positivos
     * como "piensa que puedes hacerlo".
     */
    private val HELP_PHRASES = listOf(
        "que puedes hacer",   // "¿Qué puedes hacer?", "que puedes hacer por mi"
        "que me puedo ayudar", // "¿En qué me puedo ayudar?" (caso reportado)
        "que puedo hacer",    // "¿Qué puedo hacer?"
        "me puedes ayudar",   // "¿Me puedes ayudar?"
        "me puede ayudar",    // "¿En qué me puede ayudar (ella)?"
        "que puedes ayudarme", // "¿En qué puedes ayudarme?"
        "que sabes hacer",    // "¿Qué sabes hacer?"
        "que comandos tienes", // "¿Qué comandos tienes?"
        "para que sirves"     // "¿Para qué sirves?"
    )

    /**
     * Prefijos de creación de notas (rama 0). El orden importa DENTRO de cada grupo
     * ("de " → "de:" → "del " → ": " → ":" → " ") para que extractAfterPrefix tome el
     * prefijo más largo posible; N-OBS2: "de:" DEBE ir antes del " " desnudo (si no,
     * "crea una nota de: pan" → nota "de: pan"). INVARIANTE N-OBS3: todo prefijo
     * termina en espacio o ':' (las excepciones colgantes "de"/"del" las filtra el
     * guard). "anotar" y "apunta" NO son prefijos (falsos positivos) y "escribe "
     * suelto está PROHIBIDO (solo "escribe una nota ..."). "del:" es no-goal.
     */
    private val NOTE_PREFIXES = listOf(
        // Grupo "crea" (la variante "del" cubre la contracción frecuente en dictado
        // por voz: "toma nota del pan" → "pan", no "l pan")
        "crear una nota de ", "crear una nota de:", "crear una nota del ", "crear una nota: ", "crear una nota:", "crear una nota ",
        "crea una nota de ", "crea una nota de:", "crea una nota del ", "crea una nota: ", "crea una nota:", "crea una nota ",
        // Grupo "escribe"
        "escribir una nota de ", "escribir una nota de:", "escribir una nota del ", "escribir una nota: ", "escribir una nota:", "escribir una nota ",
        "escribe una nota de ", "escribe una nota de:", "escribe una nota del ", "escribe una nota: ", "escribe una nota:", "escribe una nota ",
        // Grupo "anota" (sin preposición "de": "anota de X" no es un patrón natural)
        "anota: ", "anota:", "anota ",
        // Grupo "toma nota"
        "toma nota de ", "toma nota de:", "toma nota del ", "tomar nota de ", "tomar nota de:", "tomar nota del ",
        "toma nota: ", "tomar nota: ",
        "toma nota:", "tomar nota:", "toma nota ", "tomar nota "
    )

    open suspend fun parse(text: String): String? {
        // O6: trim COMPLETO (no solo lowercase) — " busca gatos" debe parsear igual que
        // "busca gatos". normalize queda intocada (contrato: longitud invariante); el trim
        // se aplica también a original.trim() en extractAfterPrefix (ADR-010).
        val trimmed = normalize(text).trim()

        return when {
            // ... (lógica remains the same but calls to execute are now suspend-safe)
            // 0. Notas (precedencia máxima: antes de ayuda/temporizador/alarma, que usan contains)
            NOTE_PREFIXES.any { prefix -> trimmed.startsWith(prefix) } -> {
                // O6: trimmed ya llega trimado (los ¡/¿ iniciales se convirtieron en espacio
                // y trim() los eliminó), así que el guard matchea directo. extractAfterPrefix
                // mantiene la alineación: normalize es longitud invariante y aquí ambos lados
                // van trimados (trimmed y original.trim()) → índices 1:1 (ADR-010). La rama
                // es robusta además por cleanNoteText (N-OBS3).
                val raw = extractAfterPrefix(trimmed, text, *NOTE_PREFIXES.toTypedArray())
                val noteText = cleanNoteText(raw)
                // Sin contenido → fall-through a Gemini (igual que "abre" sin argumento).
                // "X nota de/del" sin argumento extrae una "de"/"del" colgante (la variante
                // corta "X nota " traga la preposición del prefijo largo): no es contenido
                // real → también fall-through. "X nota del: pan" no matchea ningún prefijo
                // largo ("del:" no existe) → el desnudo "X nota " extrae "del: pan": el
                // guard lo rechaza también ("de:"/"del:" INICIALES = no-goal de la
                // contracción con ':', invariante N-OBS3) → Gemini.
                // El parser NO trunca: CreateNote viaja completo (el límite lo aplica la acción).
                if (noteText.isEmpty() || noteText == "de" || noteText == "del" ||
                    noteText.startsWith("de:") || noteText.startsWith("del:")
                ) {
                    null
                } else {
                    execute(SystemCommand.CreateNote(noteText))
                }
            }

            // 0b. Lectura de notas (N1). Entre la rama 0 y la 1: "lee la nota de la alarma
            // de las 7" → ReadNote("la alarma de las 7") — precedencia sobre las ramas
            // 3/12, que usan contains.
            trimmed == "lee mis notas" || trimmed == "lee mis notas:" -> {
                // ⚠️ Igualdad EXACTA (no startsWith): "lee mis notas de la semana" DEBE caer
                // a Gemini (null). El lookahead (?=\s|:|$) de la rama 1 no sirve aquí (el
                // espacio lo atraviesa). El ':' final se acepta como separador de comando
                // (H5/ADR-011). ADR-012.
                execute(SystemCommand.ReadNotes)
            }
            trimmed.startsWith("lee la nota de ") || trimmed.startsWith("lee la nota de:") ||
                trimmed.startsWith("lee la nota del ") || trimmed.startsWith("lee la nota: ") ||
                trimmed.startsWith("lee la nota:") || trimmed.startsWith("lee la nota ") -> {
                // Todos los prefijos terminan en espacio o ':' (invariante N-OBS3). "lee la
                // nota" desnudo NO está listado → null (Gemini). "lee la nota:pan" lo cubre
                // "lee la nota:" (pegado). Mismo guard de colgantes que la rama 0:
                // ""/de/del sin argumento → null (no es una búsqueda real); "lee la nota
                // del: pan" (desnudo + contracción con ':') → también null (N-OBS3).
                val raw = extractAfterPrefix(
                    trimmed, text,
                    "lee la nota de ", "lee la nota de:", "lee la nota del ",
                    "lee la nota: ", "lee la nota:", "lee la nota "
                )
                val query = cleanNoteText(raw)
                if (query.isEmpty() || query == "de" || query == "del" ||
                    query.startsWith("de:") || query.startsWith("del:")
                ) {
                    null
                } else {
                    execute(SystemCommand.ReadNote(query))
                }
            }

            // 1. Ayuda → marcador UI (sin handler)
            // trimmed ya va trimado (O6), así que el guard matchea directo, incluido
            // "¿Ayuda?"/"¡Ayuda!". M1: "ayuda:"/"comandos:" (palabra-: = separador de
            // comando, consistencia con H3). Variantes contains con límite (?=\s|:|$):
            // "piensa que puedes hacerlo" NO debe matchear "que puedes hacer" pero
            // "que puedes hacer:" SÍ (H5). Precedencia: esta rama manda sobre
            // contains("alarma") de la rama 12 ("¿qué puedes hacer con la alarma?" → HELP).
            trimmed == "ayuda" || trimmed.startsWith("ayuda ") || trimmed.startsWith("ayuda:") ||
                trimmed == "comandos" || trimmed.startsWith("comandos ") || trimmed.startsWith("comandos:") ||
                HELP_PHRASES.any { phrase ->
                    // (?=\s|:|$): límite posterior de palabra + separador ':' de comando
                    // (H5: "que puedes hacer:" y "que puedes hacer: x" → HELP; el falso
                    // positivo "piensa que puedes hacer: nada" se acepta — ADR-011).
                    // (?!\s+a\s): excluye "ayudar a + infinitivo" ("¿me puedes ayudar a poner
                    // una alarma?" NO es una petición de ayuda genérica → cae a Gemini).
                    Regex("""$phrase(?=\s|:|$)(?!\s+a\s)""").containsMatchIn(trimmed)
                } -> {
                CommandMarkers.HELP
            }

            // 2. Repite → marcador UI (sin handler)
            // H3: "repite: eso" — el ':' separador de comando (tras palabra, no dígito-:,
            // que es hora en la rama 12) también es válido; sin extracción (ADR-011).
            trimmed == "repite" || trimmed.startsWith("repite ") || trimmed.startsWith("repite:") ||
                trimmed == "otra vez" -> {
                CommandMarkers.REPEAT
            }

            // 2b. Monitoreo continuo → marcadores UI (sin handler)
            // Frases: "activar monitoreo", "iniciar monitoreo", "encender monitoreo",
            // "detener monitoreo", "parar monitoreo", "apagar monitoreo".
            // V3 DBG-veredicto: los triggers de monitoreo son igualdad exacta/prefijo
            // ("palabra", "palabra " o "palabra:"); los typos caen a IA por diseño.
            trimmed == "activar monitoreo" || trimmed.startsWith("activar monitoreo ") || trimmed.startsWith("activar monitoreo:") ||
            trimmed == "iniciar monitoreo" || trimmed.startsWith("iniciar monitoreo ") || trimmed.startsWith("iniciar monitoreo:") ||
            trimmed == "encender monitoreo" || trimmed.startsWith("encender monitoreo ") || trimmed.startsWith("encender monitoreo:") -> {
                CommandMarkers.START_MONITORING
            }
            trimmed == "detener monitoreo" || trimmed.startsWith("detener monitoreo ") || trimmed.startsWith("detener monitoreo:") ||
            trimmed == "parar monitoreo" || trimmed.startsWith("parar monitoreo ") || trimmed.startsWith("parar monitoreo:") ||
            trimmed == "apagar monitoreo" || trimmed.startsWith("apagar monitoreo ") || trimmed.startsWith("apagar monitoreo:") -> {
                CommandMarkers.STOP_MONITORING
            }

            // 2c. Análisis visual de pantalla → marcador UI (sin handler)
            // Frases: "analiza mi pantalla", "qué hay en mi pantalla", "analiza pantalla",
            // "qué ves en mi pantalla", "analizar mi pantalla", "analizar pantalla".
            trimmed == "analiza mi pantalla" || trimmed.startsWith("analiza mi pantalla ") || trimmed.startsWith("analiza mi pantalla:") ||
            trimmed == "que hay en mi pantalla" || trimmed.startsWith("que hay en mi pantalla ") || trimmed.startsWith("que hay en mi pantalla:") ||
            trimmed == "analiza pantalla" || trimmed.startsWith("analiza pantalla ") || trimmed.startsWith("analiza pantalla:") ||
            trimmed == "que ves en mi pantalla" || trimmed.startsWith("que ves en mi pantalla ") || trimmed.startsWith("que ves en mi pantalla:") ||
            trimmed == "analizar mi pantalla" || trimmed.startsWith("analizar mi pantalla ") || trimmed.startsWith("analizar mi pantalla:") ||
            trimmed == "analizar pantalla" || trimmed.startsWith("analizar pantalla ") || trimmed.startsWith("analizar pantalla:") -> {
                CommandMarkers.ANALYZE_SCREEN
            }

            // 3. Temporizador (exige duración válida; sin duración → fall-through).
            //    O5: duración COMPUESTA — se suman todas las unidades y las fracciones
            //    "y media"/"y cuarto" ("1 hora y 30 minutos" → 90, "pasa 2 horas y media" → 150).
            //    TMP-num: el guard admite también unidades habladas y "media/cuarto hora"
            //    ("una hora", "veinte y cinco minutos", "pasa media hora").
            (trimmed.contains("temporizador") || trimmed.startsWith("pasa ")) &&
                (durationUnitRegex.containsMatchIn(trimmed) ||
                    durationSpokenUnitRegex.containsMatchIn(trimmed) ||
                    durationSpokenFractionUnitRegex.containsMatchIn(trimmed)) -> {
                val minutes = parseDurationMinutes(trimmed) ?: return null
                // M1 (Lote 10): invariante del temporizador — fuera de [1, 1440] →
                // error SIN ejecutar (precedente B2 en set_alarm). La fuente única es
                // SystemCommand.TIMER_* (compartida con el wire vía AccionRegistry).
                if (minutes !in SystemCommand.TIMER_MIN_MINUTOS..SystemCommand.TIMER_MAX_MINUTOS) {
                    return SystemCommand.TIMER_ERROR_MENSAJE
                }
                execute(SystemCommand.SetTimer(minutes))
            }

            // 4a. Llamada con "al" (antes que 4b: "llama al 600" no debe dejar la "l" pegada)
            trimmed.startsWith("llama al ") || trimmed.startsWith("llamar al ") -> {
                callOrNumber(extractAfterPrefix(trimmed, text, "llama al ", "llamar al "))
            }

            // 4b. Llamada a contacto o número
            trimmed.startsWith("llama a ") || trimmed.startsWith("llamar a ") -> {
                callOrNumber(extractAfterPrefix(trimmed, text, "llama a ", "llamar a "))
            }

            // 5. Volumen
            trimmed.startsWith("sube el volumen") || trimmed.startsWith("sube volumen") ||
                trimmed.startsWith("baja el volumen") || trimmed.startsWith("baja volumen") ||
                trimmed == "silencio" || trimmed.startsWith("silencio ") ||
                trimmed == "silencia" || trimmed.startsWith("silencia ") -> {
                val action = when {
                    trimmed.contains("maximo") -> SystemCommand.SetVolume(com.screenassistant.core.domain.model.VolumeAction.MAX)
                    trimmed.contains("minimo") -> SystemCommand.SetVolume(com.screenassistant.core.domain.model.VolumeAction.MIN)
                    trimmed.startsWith("sube") -> SystemCommand.SetVolume(com.screenassistant.core.domain.model.VolumeAction.UP)
                    trimmed.startsWith("baja") -> SystemCommand.SetVolume(com.screenassistant.core.domain.model.VolumeAction.DOWN)
                    else -> SystemCommand.SetVolume(com.screenassistant.core.domain.model.VolumeAction.MUTE)
                }
                execute(action)
            }

            // 6. Idioma (otro idioma → fall-through)
            (trimmed.startsWith("habla en ") || trimmed.startsWith("cambia a ")) &&
                (trimmed.contains("ingles") || trimmed.contains("espanol")) -> {
                val language = if (trimmed.contains("ingles")) {
                    com.screenassistant.core.domain.model.AssistantLanguage.ENGLISH
                } else {
                    com.screenassistant.core.domain.model.AssistantLanguage.SPANISH
                }
                execute(SystemCommand.SetLanguage(language))
            }

            // 7. Ajustes (antes de "abre X": Ajustes no es app launcher)
            trimmed.startsWith("abre los ajustes") || trimmed.startsWith("abre la configuracion") ||
                trimmed.startsWith("abre ajustes") || trimmed.startsWith("abre configuracion") ||
                trimmed.startsWith("abrir los ajustes") || trimmed.startsWith("abrir la configuracion") -> {
                execute(SystemCommand.OpenSettings)
            }

            // 8. Abrir app (excluye "abre el archivo" que va a rama 24)
            (trimmed.startsWith("abre ") || trimmed.startsWith("abrir ")) &&
                !trimmed.startsWith("abre el archivo") && !trimmed.startsWith("abrir archivo") &&
                !trimmed.startsWith("abre archivo") && !trimmed.startsWith("buscar archivo") -> {
                val query = extractAfterPrefix(trimmed, text, "abre ", "abrir ")
                execute(SystemCommand.OpenApp(query))
            }

            // 9. Navegación
            trimmed.startsWith("llevame a ") || trimmed.startsWith("navega a ") -> {
                val destination = extractAfterPrefix(trimmed, text, "llevame a ", "navega a ")
                execute(SystemCommand.Navigate(destination))
            }

            // 10. Búsqueda en Google
            // H3: "busca:"/"buscar:" — el ':' separador de comando (tras palabra, no
            // dígito-:, que es hora en la rama 12) también es válido (ADR-011). El orden
            // de prefijos es IRRELEVANTE: "busca " y "busca:" son disjuntos en la posición
            // 5 (' ' vs ':'). El trimStart(':') limpia el bug preexistente "busca : gatos"
            // y el caso "busca: : gatos" (AJUSTE QA: trim ANTES de trimStart).
            trimmed.startsWith("busca ") || trimmed.startsWith("buscar ") ||
                trimmed.startsWith("busca:") || trimmed.startsWith("buscar:") -> {
                val query = extractAfterPrefix(trimmed, text, "busca ", "buscar ", "busca:", "buscar:")
                    .trim().trimStart(':').trim()
                execute(SystemCommand.SearchGoogle(query))
            }

            // 11. Memoria offline ("recuerda que " primero: también matchea "recuerda ")
            trimmed.startsWith("recuerda que ") || trimmed.startsWith("recuerda ") -> {
                val fact = extractAfterPrefix(trimmed, text, "recuerda que ", "recuerda ")
                execute(SystemCommand.SaveMemory(fact))
            }

            // 11b. Cancelar alarma (O4). Antes de la rama 12: "cancela la alarma de las 7"
            // hoy ejecutaba SetAlarm (bug del backlog); aquí debe ejecutar CancelAlarm.
            // Sin hora → CancelAlarm(null, null) = borrar todas.
            // O6: trimmed ya va trimado, así que el ^ del regex matchea incluso con
            // "¿Cancela la alarma?" (normalize convierte ¿/¡ en espacio y trim() lo elimina).
            cancelAlarmRegex.containsMatchIn(trimmed) -> {
                // substringAfter("alarma"): el anchor (las|la) de findMatch capturaría el
                // "la" del sustantivo y trataría "alarma de las 7" como hora → null.
                val match = TimePhraseParser.findMatch(trimmed.substringAfter("alarma"))
                execute(SystemCommand.CancelAlarm(match?.time?.hour, match?.time?.minute))
            }

            // ===== Fase 1: Capacidades offline =====

            // 13. Listar contactos — ANTES deDeviceInfo y calculator (evitar overlap)
            trimmed.startsWith("lista mis contactos") || trimmed.startsWith("listar mis contactos") ||
            trimmed.startsWith("listar contactos") || trimmed.startsWith("lista contactos") ||
            trimmed.startsWith("quienes son mis contactos") || trimmed.startsWith("quien son mis contactos") ||
            trimmed.startsWith("mostrar contactos") || trimmed.startsWith("mostrar mis contactos") -> {
                execute(SystemCommand.ListContacts)
            }

            // 14. Info WiFi
            trimmed.startsWith("cual es mi wifi") || trimmed.startsWith("cual es mi red") ||
            trimmed.startsWith("que wifi tengo") || trimmed.startsWith("que red tengo") ||
            trimmed.startsWith("nombre de mi wifi") || trimmed.startsWith("nombre de mi red") ||
            trimmed.startsWith("signal de wifi") || trimmed.startsWith("senal de wifi") ||
            trimmed.startsWith("info del wifi") || trimmed.startsWith("informacion del wifi") ||
            trimmed.startsWith("datos del wifi") || trimmed.startsWith("nivel de senal") -> {
                execute(SystemCommand.GetWifiInfo)
            }

            // 15. Info del dispositivo — ANTES de calculator (evitar overlap "cuanto es")
            trimmed.startsWith("que telefono tengo") || trimmed.startsWith("que celular tengo") ||
            trimmed.startsWith("que modelo es mi telefono") || trimmed.startsWith("que modelo es mi celular") ||
            trimmed.startsWith("que telefono es este") -> {
                execute(SystemCommand.DeviceInfo(com.screenassistant.core.domain.model.DeviceInfoType.MODEL))
            }
            trimmed.startsWith("cuanta bateria queda") || trimmed.startsWith("cuanto bateria queda") ||
            trimmed.startsWith("nivel de bateria") || trimmed.startsWith("cuanta bateria tengo") ||
            trimmed.startsWith("cuanto bateria tengo") -> {
                execute(SystemCommand.DeviceInfo(com.screenassistant.core.domain.model.DeviceInfoType.BATTERY))
            }
            trimmed.startsWith("cuanto espacio libre") || trimmed.startsWith("cuanto espacio tengo") ||
            trimmed.startsWith("cuanto almacenamiento queda") || trimmed.startsWith("cuanto almacenamiento tengo") ||
            trimmed.startsWith("espacio libre") -> {
                execute(SystemCommand.DeviceInfo(com.screenassistant.core.domain.model.DeviceInfoType.STORAGE))
            }

            // 16. Portapapeles
            trimmed == "copia" || trimmed == "copiar" || trimmed.startsWith("copia ") || trimmed.startsWith("copiar ") -> {
                val textToCopy = if (trimmed == "copia" || trimmed == "copiar") "" else extractAfterPrefix(trimmed, text, "copia ", "copiar ")
                if (textToCopy.isEmpty()) {
                    "Error: ¿Qué quieres que copie?"
                } else {
                    execute(SystemCommand.Clipboard(com.screenassistant.core.domain.model.ClipboardOperation.COPY, textToCopy))
                }
            }
            trimmed == "pega" || trimmed == "pegar" || trimmed.startsWith("pega ") || trimmed.startsWith("pegar ") -> {
                execute(SystemCommand.Clipboard(com.screenassistant.core.domain.model.ClipboardOperation.PASTE))
            }
            trimmed.startsWith("que tengo copiado") || trimmed.startsWith("que hay en el portapapeles") ||
            trimmed.startsWith("que tengo en el portapapeles") || trimmed.startsWith("contenido del portapapeles") -> {
                execute(SystemCommand.Clipboard(com.screenassistant.core.domain.model.ClipboardOperation.SHOW))
            }

            // 17. Cronómetro
            trimmed.startsWith("inicia cronometro") || trimmed.startsWith("arranca cronometro") ||
            trimmed.startsWith("empieza cronometro") || trimmed.startsWith("iniciar cronometro") -> {
                execute(SystemCommand.Stopwatch(com.screenassistant.core.domain.model.StopwatchAction.START))
            }
            trimmed.startsWith("para el cronometro") || trimmed.startsWith("para cronometro") ||
            trimmed.startsWith("deten cronometro") || trimmed.startsWith("detener cronometro") ||
            trimmed.startsWith("parar cronometro") || trimmed.startsWith("parar el cronometro") -> {
                execute(SystemCommand.Stopwatch(com.screenassistant.core.domain.model.StopwatchAction.STOP))
            }
            trimmed.startsWith("cuanto tiempo lleva") || trimmed.startsWith("cuanto va el cronometro") ||
            trimmed.startsWith("que tiempo lleva") || trimmed.startsWith("cuanto tiempo lleva el cronometro") ||
            trimmed == "cronometro" -> {
                execute(SystemCommand.Stopwatch(com.screenassistant.core.domain.model.StopwatchAction.GET_TIME))
            }

            // 18. Calculadora — DESPUÉS deDeviceInfo para evitar overlap "cuanto es"
            trimmed.startsWith("cuanto es ") || trimmed.startsWith("que es ") ||
            trimmed.startsWith("cuanto son ") || trimmed.startsWith("cuantos son ") ||
            trimmed.startsWith("que son ") ||
            trimmed.startsWith("suma ") || trimmed.startsWith("sumar ") ||
            trimmed.startsWith("resta ") || trimmed.startsWith("restar ") ||
            trimmed.startsWith("multiplica ") || trimmed.startsWith("multiplicar ") ||
            trimmed.startsWith("divide ") || trimmed.startsWith("dividir ") -> {
                parseCalculator(trimmed, text)
            }

            // ===== Fase 2: Capacidades offline =====

            // 19. Bluetooth
            trimmed.startsWith("activa el bluetooth") || trimmed.startsWith("activar bluetooth") ||
            trimmed.startsWith("encender bluetooth") || trimmed.startsWith("enciende el bluetooth") ||
            trimmed.startsWith("abre bluetooth") || trimmed.startsWith("abrir bluetooth") -> {
                execute(SystemCommand.SetBluetooth(true))
            }
            trimmed.startsWith("desactiva el bluetooth") || trimmed.startsWith("desactivar bluetooth") ||
            trimmed.startsWith("apagar bluetooth") || trimmed.startsWith("apaga el bluetooth") ||
            trimmed.startsWith("cierra bluetooth") || trimmed.startsWith("cerrar bluetooth") -> {
                execute(SystemCommand.SetBluetooth(false))
            }

            // 20. Brillo
            trimmed.startsWith("pon el brillo al ") || trimmed.startsWith("pon brillo al ") ||
            trimmed.startsWith("ajusta el brillo a ") || trimmed.startsWith("ajusta brillo a ") ||
            trimmed.startsWith("brillo al ") -> {
                val levelStr = extractAfterPrefix(trimmed, text,
                    "pon el brillo al ", "pon brillo al ", "ajusta el brillo a ",
                    "ajusta brillo a ", "brillo al ")
                    .replace(Regex("""\s+por\s+ciento\s*"""), "")
                    .replace(Regex("""\s*%\s*"""), "")
                    .trim()
                val level = levelStr.toIntOrNull()
                if (level != null && level in 0..255) {
                    execute(SystemCommand.SetBrightness(level))
                } else {
                    "Error: El brillo debe ser un número entre 0 y 255."
                }
            }
            trimmed.startsWith("sube el brillo") || trimmed.startsWith("subir brillo") ||
            trimmed.startsWith("aumenta el brillo") -> {
                execute(SystemCommand.SetBrightness(-1)) // -1 = subir
            }
            trimmed.startsWith("baja el brillo") || trimmed.startsWith("bajar brillo") ||
            trimmed.startsWith("reduce el brillo") -> {
                execute(SystemCommand.SetBrightness(-2)) // -2 = bajar
            }

            // 21. Linterna
            trimmed.startsWith("enciende la linterna") || trimmed.startsWith("activa la linterna") ||
            trimmed.startsWith("encender linterna") || trimmed.startsWith("activar linterna") ||
            trimmed.startsWith("prende la linterna") || trimmed == "linterna" -> {
                execute(SystemCommand.SetFlashlight(true))
            }
            trimmed.startsWith("apaga la linterna") || trimmed.startsWith("desactiva la linterna") ||
            trimmed.startsWith("apagar linterna") || trimmed.startsWith("desactivar linterna") ||
            trimmed.startsWith("apaga linterna") -> {
                execute(SystemCommand.SetFlashlight(false))
            }

            // 22. Modo avión
            trimmed.startsWith("activa el modo avion") || trimmed.startsWith("activar modo avion") ||
            trimmed.startsWith("pon modo avion") || trimmed.startsWith("enciende modo avion") ||
            trimmed.startsWith("modos avion") -> {
                execute(SystemCommand.SetAirplaneMode(true))
            }
            trimmed.startsWith("desactiva el modo avion") || trimmed.startsWith("desactivar modo avion") ||
            trimmed.startsWith("quita modo avion") || trimmed.startsWith("apaga modo avion") -> {
                execute(SystemCommand.SetAirplaneMode(false))
            }

            // 23. Datos móviles
            trimmed.startsWith("activa los datos moviles") || trimmed.startsWith("activar datos moviles") ||
            trimmed.startsWith("enciende los datos") || trimmed.startsWith("activa datos moviles") -> {
                execute(SystemCommand.SetMobileData(true))
            }
            trimmed.startsWith("desactiva los datos moviles") || trimmed.startsWith("desactivar datos moviles") ||
            trimmed.startsWith("apaga los datos") || trimmed.startsWith("desactiva datos moviles") -> {
                execute(SystemCommand.SetMobileData(false))
            }

            // 24. Abrir archivo
            trimmed.startsWith("abre el archivo ") || trimmed.startsWith("abrir archivo ") ||
            trimmed.startsWith("abre archivo ") || trimmed.startsWith("buscar archivo ") -> {
                val query = extractAfterPrefix(trimmed, text,
                    "abre el archivo ", "abrir archivo ", "abre archivo ", "buscar archivo ")
                execute(SystemCommand.OpenFile(query))
            }

            // 25. Historial de llamadas
            trimmed.startsWith("historial de llamadas") || trimmed.startsWith("llamadas recientes") ||
            trimmed.startsWith("que llamadas he hecho") || trimmed.startsWith("llamadas") -> {
                execute(SystemCommand.CallHistory)
            }

            // ===== Fase 3: Capacidades offline con ML Kit =====

            // 26. Leer código QR
            trimmed.startsWith("escanea codigo qr") || trimmed.startsWith("escanear codigo qr") ||
            trimmed.startsWith("lee el codigo qr") || trimmed.startsWith("leer codigo qr") ||
            trimmed.startsWith("escanea qr") || trimmed.startsWith("codigo qr") -> {
                execute(SystemCommand.ScanQr)
            }

            // 27. OCR offline
            trimmed.startsWith("lee el texto de la pantalla") || trimmed.startsWith("leer texto de la pantalla") ||
            trimmed.startsWith("que texto hay en la pantalla") || trimmed.startsWith("ocr") ||
            trimmed.startsWith("lee la pantalla") || trimmed.startsWith("leer la pantalla") -> {
                execute(SystemCommand.OcrScan)
            }

            // 28. Traducción offline
            trimmed.startsWith("traduce ") || trimmed.startsWith("traducir ") -> {
                val raw = extractAfterPrefix(trimmed, text, "traduce ", "traducir ")
                val targetLang = when {
                    raw.contains("al ingles") || raw.contains("a english") || raw.contains("en ingles") ->
                        com.screenassistant.core.domain.model.TranslateLanguage.ENGLISH
                    raw.contains("al frances") || raw.contains("a francais") || raw.contains("en frances") ->
                        com.screenassistant.core.domain.model.TranslateLanguage.FRENCH
                    raw.contains("al aleman") || raw.contains("a deutsch") || raw.contains("en aleman") ->
                        com.screenassistant.core.domain.model.TranslateLanguage.GERMAN
                    raw.contains("al portugues") || raw.contains("a portugues") || raw.contains("en portugues") ->
                        com.screenassistant.core.domain.model.TranslateLanguage.PORTUGUESE
                    raw.contains("al chino") || raw.contains("a chino") || raw.contains("en chino") ->
                        com.screenassistant.core.domain.model.TranslateLanguage.CHINESE
                    raw.contains("al japones") || raw.contains("a japones") || raw.contains("en japones") ->
                        com.screenassistant.core.domain.model.TranslateLanguage.JAPANESE
                    else -> com.screenassistant.core.domain.model.TranslateLanguage.ENGLISH
                }
                val textToTranslate = raw
                    .replace(Regex("""\s*al?\s+(?:ingles|english|frances|francais|aleman|deutsch|portugues|portugais|chino|chinese|japones|japanese)\s*"""), "")
                    .trim()
                if (textToTranslate.isEmpty()) {
                    "Error: ¿Qué texto quieres que traduzca?"
                } else {
                    execute(SystemCommand.TranslateText(textToTranslate, targetLang))
                }
            }

            // 29. Reconocimiento facial
            trimmed.startsWith("detecta caras") || trimmed.startsWith("detectar caras") ||
            trimmed.startsWith("hay caras") || trimmed.startsWith("reconoce caras") ||
            trimmed == "reconocimiento facial" || trimmed == "cara" -> {
                execute(SystemCommand.DetectFace)
            }

            // ===== Fase 4: Hardware directo =====

            // 30. Vibración
            trimmed.startsWith("vibra ") || trimmed.startsWith("vibrar ") ||
            trimmed == "vibra" || trimmed == "vibrar" -> {
                val arg = if (trimmed == "vibra" || trimmed == "vibrar") "" else
                    trimmed.removePrefix("vibra ").removePrefix("vibrar ").trim()
                parseVibration(arg)
            }

            // 31. Ubicación GPS
            trimmed.startsWith("donde estoy") || trimmed.startsWith("dónde estoy") ||
            trimmed.startsWith("mi ubicacion") || trimmed.startsWith("mi ubicación") ||
            trimmed.startsWith("que ubicacion tengo") || trimmed.startsWith("que ubicación tengo") ||
            trimmed == "ubicacion" || trimmed == "ubicación" ||
            trimmed.startsWith("coordenadas") || trimmed.startsWith("latitud") ||
            trimmed.startsWith("donde me encuentro") -> {
                execute(SystemCommand.GetLocation)
            }

            // 32. WiFi toggle
            trimmed.startsWith("enciende el wifi") || trimmed.startsWith("encender wifi") ||
            trimmed.startsWith("activa el wifi") || trimmed.startsWith("activar wifi") ||
            trimmed.startsWith("prende el wifi") || trimmed == "wifi" -> {
                execute(SystemCommand.SetWifi(true))
            }
            trimmed.startsWith("apaga el wifi") || trimmed.startsWith("apagar wifi") ||
            trimmed.startsWith("desactiva el wifi") || trimmed.startsWith("desactivar wifi") ||
            trimmed.startsWith("cierra el wifi") -> {
                execute(SystemCommand.SetWifi(false))
            }

            // 33. Captura de cámara
            trimmed.startsWith("saca una foto") || trimmed.startsWith("tomar foto") ||
            trimmed.startsWith("toma una foto") || trimmed.startsWith("sacar foto") ||
            trimmed == "foto" || trimmed == "captura" || trimmed == "selfie" ||
            trimmed.startsWith("captura foto") || trimmed.startsWith("foto con la camara") -> {
                val useFront = trimmed.contains("frontal") || trimmed.contains("delantera") ||
                    trimmed.contains("selfie")
                execute(SystemCommand.TakePhoto(useFront))
            }

            // 34. Personalidad J.A.R.V.I.S. (Modos)
            trimmed.contains("protocolo centinela") || trimmed.contains("mantente alerta") || 
            trimmed == "modo centinela" -> {
                execute(SystemCommand.SetAssistantMode(AssistantMode.CENTINELA))
            }
            trimmed.contains("modo tactico") || trimmed.contains("solo emergencias") || 
            trimmed.contains("activa modo tactico") -> {
                execute(SystemCommand.SetAssistantMode(AssistantMode.TACTICO))
            }
            trimmed.contains("silencio de radio") || trimmed.contains("solo responde si te hablo") || 
            trimmed == "modo silencioso" -> {
                execute(SystemCommand.SetAssistantMode(AssistantMode.SILENCIOSO))
            }

            // 12. Alarma: con hora hablada → SetAlarm; sin hora → OpenAlarms
            // B1: el anchor (las|la) de TimePhraseParser capturaba el "la" del sustantivo
            // ("pon la alarma a las 7" → hora "alarma" → null → OpenAlarms). Igual que la
            // rama 11b, la hora se busca SOLO sobre el subtexto posterior a "alarma".
            trimmed.contains("alarma") -> {
                val base = trimmed.substringAfter("alarma")
                val match = TimePhraseParser.findMatch(base)
                if (match != null) {
                    // La etiqueta "para X" se re-ancla sobre el MISMO subtexto (base) y
                    // SOLO tras el match de hora: "pon la alarma para las 8" no tiene
                    // etiqueta (el "para" previo es preposición de la hora) y "pon la
                    // alarma a las 7:30 para despertarme" → "despertarme". Se extrae del
                    // ORIGINAL preservando el case (normalización 1:1 → índices válidos).
                    val paraIdx = base.indexOf("para ", match.matchEnd)
                    val label = if (paraIdx >= 0) {
                        text.substringAfter("alarma").substring(paraIdx + 5).trim().ifBlank { null }
                    } else {
                        null
                    }
                    execute(SystemCommand.SetAlarm(match.time.hour, match.time.minute, label))
                } else {
                    execute(SystemCommand.OpenAlarms)
                }
            }

            else -> null // No es un comando directo, se delega a Gemini
        }
    }

    /**
     * Parsea y ejecuta un comando simple SIN detección de conectores de secuencia.
     *
     * Firma y semántica idéntica a {@link #parse(String)}: devuelve el resultado
     * de la ejecución ({@code "Éxito: ..."} | {@code "Error: ..."}) o {@code null}
     * si no es un comando directo (delega a Gemini).
     *
     * Expuesto públicamente para que {@link SequenceParserImpl} pueda reutilizar
     * la lógica de parsing/ejecución de comandos atómicos.
     *
     * @see #parse(String)
     */
    suspend fun parseSingleCommand(text: String): String? = parse(text)

    // M2 (Lote 10): sufijo de cortesía del dictado ("por favor"/"porfavor"/"porfa",
    // case-insensitive, con separadores opcionales) — es un artefacto de la voz, no
    // parte del argumento de la llamada. Sin esto, "llama a 600 123 456 por favor"
    // NO matchea phoneRegex (matches = cadena completa) → Call("600 123 456 por favor").
    private val cortesiaSufijoRegex = Regex("""(?i)\s*(?:por\s+favor|porfavor|porfa)\s*$""")

    private suspend fun callOrNumber(arg: String): String {
        val limpio = cortesiaSufijoRegex.replace(arg.trim(), "").trim()
        // M2: "llama a por favor" → sin target tras el strip → error SIN ejecutar
        // (antes emitía Call("por favor"), contacto basura).
        if (limpio.isEmpty()) return "Error: ¿A quién quieres que llame?"
        return if (limpio.matches(phoneRegex)) {
            // H4: el strip quita también los puntos (mantiene el '+' actual).
            execute(SystemCommand.CallNumber(limpio.replace(Regex("""[.\s-]"""), "")))
        } else {
            execute(SystemCommand.Call(limpio))
        }
    }

    /**
     * O5+TMP-2+TMP-num: total de minutos de una duración hablada. Suma en este orden:
     *  1. Unidades numéricas (hora×60, minuto×1, segundo→0, solo activa el ceil).
     *  2. Unidades habladas ("una hora", "veinte y cinco minutos") resueltas con
     *     SpokenNumber; cantidad no resoluble → null (Gemini, nunca parcial).
     *  3. Fracción "y media/cuarto" ligada a horas ("1 hora y media" → +30/+15).
     *  3b. Fracción con unidad propia ("media hora", "cuarto de hora" → +30/+15).
     *  4. Dígito desnudo "X horas/minutos y N" → N minutos (0..59; >59 → null; el
     *     lookahead de durationBareRegex bloquea el doble conteo con unidad explícita).
     *  5. "X minutos y media/cuarto" residual (no ligado a horas) → null.
     *  6. Solo segundos → ceil a 1 minuto ("45 segundos" → 1).
     */
    private fun parseDurationMinutes(trimmed: String): Int? {
        // 1. Unidades numéricas.
        var numMinutes = 0
        var hasSeconds = false
        for (m in durationUnitRegex.findAll(trimmed)) {
            when {
                m.groupValues[2].startsWith("hora") -> numMinutes += m.groupValues[1].toInt() * 60
                m.groupValues[2].startsWith("minuto") -> numMinutes += m.groupValues[1].toInt()
                else -> hasSeconds = true
            }
        }
        // 2. Unidades habladas (cantidad no resoluble → rechazo completo).
        var spokenMinutes = 0
        for (m in durationSpokenUnitRegex.findAll(trimmed)) {
            val quantity = SpokenNumber.resolve(
                m.groupValues[1], m.groupValues[2].takeIf { it.isNotEmpty() }
            ) ?: return null
            when {
                m.groupValues[3].startsWith("hora") -> spokenMinutes += quantity * 60
                m.groupValues[3].startsWith("minuto") -> spokenMinutes += quantity
                else -> hasSeconds = true
            }
        }
        // 3. Fracciones ligadas a horas compuestas (grupo 1 = media|cuarto).
        //    M3: findAll + suma (ADR-TMP-1) — "1 hora y media y 2 horas y cuarto" → +45.
        val fraction = durationFractionRegex.findAll(trimmed).map { m ->
            if (m.groupValues[1] == "media") 30 else 15
        }.sum()
        // 3b. Fracción con unidad propia.
        val fractionUnit = durationSpokenFractionUnitRegex.find(trimmed)
            ?.let { if (it.groupValues[1] == "media") 30 else 15 } ?: 0
        // 4. Dígito desnudo (TMP-2): siempre MINUTOS; trailing sin unidad se ignora.
        val bare = durationBareRegex.find(trimmed)?.let { m ->
            val n = m.groupValues[2].toInt()
            if (n !in 0..59) return null else n
        } ?: 0
        // 5. Residuales → ambiguo/compuesto inválido, rechazo completo (Gemini):
        //    - "X minutos y media/cuarto" no ligado a horas ("3 minutos y medio").
        //    - Decenas no soportadas >59 ("sesenta y cinco minutos") — B1.
        //    - Compuesto inválido con decena soportada ("veinte y diez minutos") — B1.
        if (minuteFractionResidualRegex.containsMatchIn(trimmed) ||
            unsupportedTensRegex.containsMatchIn(trimmed) ||
            invalidCompoundRegex.containsMatchIn(trimmed)
        ) return null
        // 6. Solo segundos → ceil a 1 minuto.
        val ceilSeconds = if (numMinutes == 0 && spokenMinutes == 0 && hasSeconds) 1 else 0
        return numMinutes + spokenMinutes + fraction + fractionUnit + bare + ceilSeconds
    }

    private suspend fun execute(command: SystemCommand): String {
        return when (val result = systemAction.execute(command)) {
            is ActionResult.Success -> result.message
            is ActionResult.Error -> "Error: ${result.reason}"
        }
    }

    /**
     * Parsea una expresión matemática en lenguaje natural y la ejecuta.
     * Soporta: "cuanto es 5 por 7", "suma 3 y 4", "resta 5 de 10",
     * "multiplica 12 por 5", "divide 10 entre 2".
     *
     * MATH (ADR-MATH, P4): emisión en dos niveles (compatibilidad):
     *  1. Si la forma legacy matchea binario simple COMPLETO (`num op num`) →
     *     `Calculate` legacy (los 12 tests viejos siguen verdes sin tocarse).
     *  2. Si no, segmento ORIGINAL → `MathExpressionNormalizer` → canónica:
     *     binaria simple → `Calculate` legacy; compleja válida →
     *     `CalculateExpression`; inválida/incompleta → null (Nivel 2).
     */
    private suspend fun parseCalculator(trimmed: String, original: String): String? {
        val prefixes = listOf(
            "cuanto es ", "que es ", "cuanto son ", "cuantos son ", "que son ",
            "suma ", "sumar ", "resta ", "restar ",
            "multiplica ", "multiplicar ", "divide ", "dividir ", "calcula ", "calcular "
        )
        val prefix = prefixes.firstOrNull { trimmed.startsWith(it) } ?: return null
        
        // Extraer segmento post-trigger preservando símbolos decimales y puntuación interna
        val rawSegment = original.trim().substring(prefix.length)
            .trim().trim(' ', '?', '!', '¡', '¿', ';', ':').trim()
        val trimmedSegment = trimmed.substring(prefix.length)

        // J.A.R.V.I.S. v3.8.4: Especial para "resta X de Y" (invertido)
        var preNormalized = trimmedSegment
        if (prefix.startsWith("resta")) {
            val match = Regex("""(\d+(?:\.\d+)?)\s+de\s+(\d+(?:\.\d+)?)""").find(trimmedSegment)
            if (match != null) {
                preNormalized = "${match.groupValues[2]} - ${match.groupValues[1]}"
            }
        }

        // Intento 1: Binario simple directo (legacy regex) para máxima velocidad
        val normalizedSimple = preNormalized.replace(Regex("""\s+y\s+"""), " + ")
            .replace(Regex("""\s*por\s*"""), " * ")
            .replace(Regex("""\s*entre\s*"""), " / ")
            .replace(Regex("""\s*mas\s*"""), " + ")
            .replace(Regex("""\s*menos\s*"""), " - ")
        
        val calcSimpleRegex = Regex("""^\s*(\d+(?:\.\d+)?)\s*([+\-*/])\s*(\d+(?:\.\d+)?)\s*$""")
        val simpleMatch = calcSimpleRegex.matchEntire(normalizedSimple)
        
        if (simpleMatch != null) {
            val op1 = simpleMatch.groupValues[1].toDoubleOrNull() ?: return null
            val operator = when (simpleMatch.groupValues[2]) {
                "+" -> CalculatorOperator.ADD
                "-" -> CalculatorOperator.SUBTRACT
                "*" -> CalculatorOperator.MULTIPLY
                "/" -> CalculatorOperator.DIVIDE
                else -> null
            }
            val op2 = simpleMatch.groupValues[3].toDoubleOrNull() ?: return null
            if (operator != null) return execute(SystemCommand.Calculate(op1, operator, op2))
        }

        // Intento 2: Normalización compleja (Español natural, raíces, potencias, etc.)
        val canonical = MathExpressionNormalizer.toCanonical(preNormalized, rawSegment)
            ?: return null

        // Si la normalización compleja devolvió un binario simple canónico (negativos incluidos)
        val canonicalSimple = Regex("""^(-?\d+(?:\.\d+)?)\s*([+\-*/])\s*(-?\d+(?:\.\d+)?)$""")
            .matchEntire(canonical)
        
        if (canonicalSimple != null) {
            val op1 = canonicalSimple.groupValues[1].toDoubleOrNull() ?: return null
            val operator = when (canonicalSimple.groupValues[2]) {
                "+" -> CalculatorOperator.ADD
                "-" -> CalculatorOperator.SUBTRACT
                "*" -> CalculatorOperator.MULTIPLY
                "/" -> CalculatorOperator.DIVIDE
                else -> null
            }
            val op2 = canonicalSimple.groupValues[3].toDoubleOrNull() ?: return null
            if (operator != null) return execute(SystemCommand.Calculate(op1, operator, op2))
        }

        // Caso General: Expresión matemática compleja
        return execute(SystemCommand.CalculateExpression(canonical))
    }

    // Decide con trimmed (normalizado + trimado — O6) pero extrae sobre el texto original
    // preservando el case del argumento: el prefijo ASCII tiene la MISMA longitud en ambos.
    private fun extractAfterPrefix(trimmed: String, original: String, vararg prefixes: String): String {
        // O6: trimmed llega SIN espacios (parse() hace normalize(text).trim()), igual que
        // original.trim() → los índices quedan alineados 1:1 y substring(prefix.length)
        // recae sobre el carácter correcto. El trim() interno se mantiene como red
        // defensiva (idempotente aquí) por si una futura rama pasa algo sin trimar.
        // M2: limpieza de bordes de puntuación residual de dictado (misma lista que
        // cleanNoteText) — O6 expuso "¿abre whatsapp?" → OpenApp("whatsapp?") (el '?'
        // final se colaba en el argumento; antes caía a Gemini por el espacio inicial).
        // Solo bordes: la puntuación INTERIOR del argumento no se toca. En la rama 0
        // esta limpieza es redundante con cleanNoteText (idempotente, resultado igual).
        val prefix = prefixes.first { trimmed.trim().startsWith(it) }
        return original.trim().substring(prefix.length)
            .trim()
            .trimStart(' ', '.', ',', ';', '?', '!', '¿', '¡', ':')
            .trimEnd(' ', '.', ',', ';', '?', '!', '¿', '¡', ':')
    }

    /**
     * Parsea el argumento de vibración: "2 segundos", "500", "sos", "llamada", etc.
     * Si es un número puro se interpreta como milisegundos.
     * Si es un patrón conocido (sos, llamada, alarma) se usa el patrón.
     */
    private suspend fun parseVibration(arg: String): String {
        val trimmedArg = arg.trim().lowercase()
        if (trimmedArg.isEmpty()) {
            return execute(SystemCommand.Vibrate(500)) // Default: 500ms
        }
        // Patrones conocidos
        val patterns = listOf("sos", "llamada", "alarma")
        if (trimmedArg in patterns) {
            return execute(com.screenassistant.core.domain.model.SystemCommand.Vibrate(0))
            // El parser no puede distinguir patrones vs duración —
            // delegamos al action que maneja ambos casos.
        }
        // Extraer número: "2 segundos", "500 ms", "1.5 segundos", "500"
        val numberRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:segundos?|s|ms|milisegundos?)?""")
        val match = numberRegex.find(trimmedArg)
        if (match != null) {
            val value = match.groupValues[1].toDoubleOrNull() ?: return "Error: Duración no válida."
            val durationMs = if (trimmedArg.contains("ms") || trimmedArg.contains("milisegundos")) {
                value.toLong()
            } else {
                (value * 1000).toLong() // Convertir segundos a ms
            }
            return execute(SystemCommand.Vibrate(durationMs))
        }
        return "Error: No entendí la duración. Usa algo como '2 segundos' o '500'."
    }

    /**
     * Limpieza de bordes de la nota extraída: quita la puntuación de los extremos
     * del texto ORIGINAL (espacios, puntos, comas, comillas, signos de exclamación
     * e interrogación) pero NO toca la puntuación interna.
     */
    private fun cleanNoteText(raw: String): String =
        raw.trim()
            .trimStart(' ', '.', ',', ';', '?', '!', '¿', '¡', ':')
            .trimEnd(' ', '.', ',', ';', '?', '!', '¿', '¡', ':')

    /**
     * lowercase + sin tildes + ñ→n + signos de puntuación → espacio.
     * Longitud invariante (cada char → 1 char; seguro para substring sobre el original).
     * ':' queda EXCLUIDO a propósito: la rama alarma delega a TimePhraseParser sobre
     * este texto y la hora numérica "19:45" necesita los dos puntos.
     */
    private fun normalize(text: String): String =
        text.lowercase()
            .replace('á', 'a').replace('é', 'e').replace('í', 'i')
            .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')
            .replace('ñ', 'n')
            .replace('¿', ' ').replace('¡', ' ').replace('?', ' ').replace('!', ' ')
            .replace('.', ' ').replace(',', ' ').replace(';', ' ')
}
