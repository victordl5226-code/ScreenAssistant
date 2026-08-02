package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.model.ActionResult
import com.screenassistant.core.domain.model.CommandMarkers
import com.screenassistant.core.domain.model.SystemCommand
import kotlinx.coroutines.runBlocking

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

    open fun parse(text: String): String? {
        // O6: trim COMPLETO (no solo lowercase) — " busca gatos" debe parsear igual que
        // "busca gatos". normalize queda intocada (contrato: longitud invariante); el trim
        // se aplica también a original.trim() en extractAfterPrefix (ADR-010).
        val trimmed = normalize(text).trim()

        return when {
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

            // 8. Abrir app
            trimmed.startsWith("abre ") || trimmed.startsWith("abrir ") -> {
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

            // 12. Alarma: con hora hablada → SetAlarm; sin hora → OpenAlarms
            trimmed.contains("alarma") -> {
                val match = TimePhraseParser.findMatch(trimmed)
                if (match != null) {
                    val paraIdx = text.indexOf("para ", match.matchEnd)
                    val label = if (paraIdx >= 0) text.substring(paraIdx + 5).trim().ifBlank { null } else null
                    execute(SystemCommand.SetAlarm(match.time.hour, match.time.minute, label))
                } else {
                    execute(SystemCommand.OpenAlarms)
                }
            }

            else -> null // No es un comando directo, se delega a Gemini
        }
    }

    private fun callOrNumber(arg: String): String {
        return if (arg.matches(phoneRegex)) {
            // H4: el strip quita también los puntos (mantiene el '+' actual).
            execute(SystemCommand.CallNumber(arg.replace(Regex("""[.\s-]"""), "")))
        } else {
            execute(SystemCommand.Call(arg))
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

    private fun execute(command: SystemCommand): String = runBlocking {
        when (val result = systemAction.execute(command)) {
            is ActionResult.Success -> result.message
            is ActionResult.Error -> "Error: ${result.reason}"
        }
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
