package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.model.CommandSequence
import com.screenassistant.core.domain.model.CommandStep
import com.screenassistant.core.domain.model.ConnectorType
import com.screenassistant.core.domain.model.SystemCommand

/**
 * Implementación concreta de SequenceParser.
 *
 * Responsabilidades:
 * 1. Normalizar el texto para detección de conectores (preserva `,`/`;`)
 * 2. Detectar el PRIMER conector con precedencia
 * 3. Dividir todo el texto por el separador correspondiente (multi-paso)
 * 4. Parsear cada segmento a SystemCommand o CommandStep.Marker
 * 5. Validar: segmento inválido → null → fallback a Gemini
 *
 * NO inyecta SystemCommandParser; usa método interno parseCommand() del parser
 * (mismo módulo core:domain). parseSingleCommand() se usa para compatibilidad legacy.
 */
class SequenceParserImpl : SequenceParser {

    private val MAX_STEPS = 10

    // ===== Detección de conectores (para determinar TIPO y SEPARADOR) =====
    // Cada patrón tiene: regex para detectar Y el separador para splitear

    private data class ConnectorDef(
        val type: ConnectorType,
        /** Regex que detecta el conector en el texto normalizado (sin tilde, lowercase) */
        val detect: Regex,
        /** Regex para SPLITEAR el texto en segmentos (aplicado al texto NORMALIZADO para conectores) */
        val split: Regex,
        /** Para PRIMERO_LUEGO: strip del prefijo "primero " antes de splitear */
        val stripPrefix: String? = null
    )

    private val connectors = listOf(
        // PRIMERO "primero X luego/después Y" → split por "luego|después"
        // El split opcionalmente consume "y" intermedio: "primero X y luego Y" o "primero X luego Y"
        ConnectorDef(
            type = ConnectorType.PRIMERO_LUEGO,
            detect = Regex("""\bprimero\s+.+\s+(?:luego|despues)\s+"""),
            split = Regex("""\s+y?\s*(?:luego|despues)\s+"""),
            stripPrefix = "primero "
        ),
        // Y_DESPUES "X y después/despues Y" → split por "y despues" (NO "y luego" — eso es Y_LUEGO)
        ConnectorDef(
            type = ConnectorType.Y_DESPUES,
            detect = Regex("""\s+y\s+despues\s+"""),
            split = Regex("""\s+y\s+despues\s+""")
        ),
        // Y_LUEGO "X y luego Y" → split por "y luego"
        ConnectorDef(
            type = ConnectorType.Y_LUEGO,
            detect = Regex("""\s+y\s+luego\s+"""),
            split = Regex("""\s+y\s+luego\s+""")
        ),
        // COMA_LUEGO "X, luego/después Y" → split por ", luego" (REQUIERE coma)
        ConnectorDef(
            type = ConnectorType.COMA_LUEGO,
            detect = Regex(""",\s*(?:luego|despues)\s+"""),
            split = Regex(""",\s*(?:luego|despues)\s+""")
        ),
        // PUNTOCOMA_LUEGO "X; luego Y" → split por "; luego"
        ConnectorDef(
            type = ConnectorType.PUNTOCOMA_LUEGO,
            detect = Regex(""";\s*(?:luego|despues)\s+"""),
            split = Regex(""";\s*(?:luego|despues)\s+""")
        )
    )

    override fun parse(text: String): CommandSequence? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null

        // Normalizar SOLO para detección de conectores (preserva `,`/`;`)
        val normalized = normalizeForConnectors(trimmed)
        if (normalized.isBlank()) return null

        // Detectar PRIMER conector (por precedencia)
        val connector = connectors.firstOrNull { it.detect.containsMatchIn(normalized) }
            ?: return null // Sin conectores → flujo legacy

        // Preparar texto para split: quitar prefijo "primero " si aplica
        val textToSplit = if (connector.stripPrefix != null) {
            normalized.replaceFirst(connector.stripPrefix, "")
        } else {
            normalized
        }

        // Dividir por el separador del conector
        val rawSegments = connector.split.split(textToSplit)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (rawSegments.size < 2 || rawSegments.size > MAX_STEPS) return null

        // Mapear cada segmento a CommandStep
        val steps = mutableListOf<CommandStep>()
        for (segment in rawSegments) {
            val step = segmentToStep(segment)
                ?: return null // Segmento no reconocido → fallback a Gemini
            steps.add(step)
        }

        return CommandSequence(
            steps = steps,
            originalText = text,
            connectorType = connector.type
        )
    }

    /**
     * Convierte un segmento normalizado a CommandStep.
     * Primero intenta parseCommand() (comandos directos).
     * Si devuelve null, intenta detectMarker() (ayuda, repite, monitoreo, análisis).
     * Si nada matchea → null (segmento inválido).
     */
    private fun segmentToStep(segment: String): CommandStep? {
        val command = parseCommand(segment)
        if (command != null) return CommandStep.Command(command)

        // Intentar detectar marcador
        val marker = detectMarker(segment) ?: return null
        return CommandStep.Marker(marker)
    }

    /**
     * Detecta si el texto es un marcador conocido (sin parsear a SystemCommand).
     * Devuelve la constante de CommandMarkers.* o null.
     */
    private fun detectMarker(text: String): String? {
        val t = text.lowercase().trim()
        return when {
            // Ayuda
            t == "ayuda" || t.startsWith("ayuda ") || t.startsWith("ayuda:") ||
                t == "comandos" || t.startsWith("comandos ") || t.startsWith("comandos:") ||
                HELP_PHRASES.any { phrase -> phrase in t } ->
                com.screenassistant.core.domain.model.CommandMarkers.HELP

            // Repite
            t == "repite" || t.startsWith("repite ") || t.startsWith("repite:") ||
                t == "otra vez" ->
                com.screenassistant.core.domain.model.CommandMarkers.REPEAT

            // Activar monitoreo
            t == "activar monitoreo" || t.startsWith("activar monitoreo ") || t.startsWith("activar monitoreo:") ||
                t == "iniciar monitoreo" || t.startsWith("iniciar monitoreo ") || t.startsWith("iniciar monitoreo:") ||
                t == "encender monitoreo" || t.startsWith("encender monitoreo ") || t.startsWith("encender monitoreo:") ->
                com.screenassistant.core.domain.model.CommandMarkers.START_MONITORING

            // Detener monitoreo
            t == "detener monitoreo" || t.startsWith("detener monitoreo ") || t.startsWith("detener monitoreo:") ||
                t == "parar monitoreo" || t.startsWith("parar monitoreo ") || t.startsWith("parar monitoreo:") ||
                t == "apagar monitoreo" || t.startsWith("apagar monitoreo ") || t.startsWith("apagar monitoreo:") ->
                com.screenassistant.core.domain.model.CommandMarkers.STOP_MONITORING

            // Análisis de pantalla
            t == "analiza mi pantalla" || t.startsWith("analiza mi pantalla ") || t.startsWith("analiza mi pantalla:") ||
                t == "que hay en mi pantalla" || t.startsWith("que hay en mi pantalla ") || t.startsWith("que hay en mi pantalla:") ||
                t == "analiza pantalla" || t.startsWith("analiza pantalla ") || t.startsWith("analiza pantalla:") ||
                t == "que ves en mi pantalla" || t.startsWith("que ves en mi pantalla ") || t.startsWith("que ves en mi pantalla:") ||
                t == "analizar mi pantalla" || t.startsWith("analizar mi pantalla ") || t.startsWith("analizar mi pantalla:") ||
                t == "analizar pantalla" || t.startsWith("analizar pantalla ") || t.startsWith("analizar pantalla:") ->
                com.screenassistant.core.domain.model.CommandMarkers.ANALYZE_SCREEN

            else -> null
        }
    }

    /**
     * Parsea un segmento a SystemCommand SIN ejecutarlo.
     * Método package-private: visible solo en core:domain para SequenceParserImpl.
     * Lógica idéntica a SystemCommandParser.parse() pero SIN la llamada a execute().
     */
    internal fun parseCommand(text: String): SystemCommand? {
        val trimmed = normalize(text).trim()
        if (trimmed.isBlank()) return null

        return when {
            // 0. Notas crear
            NOTE_PREFIXES.any { prefix -> trimmed.startsWith(prefix) } -> {
                val raw = extractAfterPrefix(trimmed, text, *NOTE_PREFIXES.toTypedArray())
                val noteText = cleanNoteText(raw)
                if (noteText.isEmpty() || noteText == "de" || noteText == "del" ||
                    noteText.startsWith("de:") || noteText.startsWith("del:")
                ) {
                    null
                } else {
                    SystemCommand.CreateNote(noteText)
                }
            }

            // 0b. Lectura de notas
            trimmed == "lee mis notas" || trimmed == "lee mis notas:" -> {
                SystemCommand.ReadNotes
            }
            trimmed.startsWith("lee la nota de ") || trimmed.startsWith("lee la nota de:") ||
                trimmed.startsWith("lee la nota del ") || trimmed.startsWith("lee la nota: ") ||
                trimmed.startsWith("lee la nota:") || trimmed.startsWith("lee la nota ") -> {
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
                    SystemCommand.ReadNote(query)
                }
            }

            // 1. Ayuda → marcador
            trimmed == "ayuda" || trimmed.startsWith("ayuda ") || trimmed.startsWith("ayuda:") ||
                trimmed == "comandos" || trimmed.startsWith("comandos ") || trimmed.startsWith("comandos:") ||
                HELP_PHRASES.any { phrase ->
                    Regex("""$phrase(?=\s|:|$)(?!\s+a\s)""").containsMatchIn(trimmed)
                } -> {
                // Marcadores se manejan aparte, aquí null para que SequenceParser los trate como marcadores
                null
            }

            // 2. Repite → marcador
            trimmed == "repite" || trimmed.startsWith("repite ") || trimmed.startsWith("repite:") ||
                trimmed == "otra vez" -> null

            // 2b. Monitoreo → marcadores
            trimmed == "activar monitoreo" || trimmed.startsWith("activar monitoreo ") || trimmed.startsWith("activar monitoreo:") ||
            trimmed == "iniciar monitoreo" || trimmed.startsWith("iniciar monitoreo ") || trimmed.startsWith("iniciar monitoreo:") ||
            trimmed == "encender monitoreo" || trimmed.startsWith("encender monitoreo ") || trimmed.startsWith("encender monitoreo:") -> null
            trimmed == "detener monitoreo" || trimmed.startsWith("detener monitoreo ") || trimmed.startsWith("detener monitoreo:") ||
            trimmed == "parar monitoreo" || trimmed.startsWith("parar monitoreo ") || trimmed.startsWith("parar monitoreo:") ||
            trimmed == "apagar monitoreo" || trimmed.startsWith("apagar monitoreo ") || trimmed.startsWith("apagar monitoreo:") -> null

            // 2c. Análisis visual → marcador
            trimmed == "analiza mi pantalla" || trimmed.startsWith("analiza mi pantalla ") || trimmed.startsWith("analiza mi pantalla:") ||
            trimmed == "que hay en mi pantalla" || trimmed.startsWith("que hay en mi pantalla ") || trimmed.startsWith("que hay en mi pantalla:") ||
            trimmed == "analiza pantalla" || trimmed.startsWith("analiza pantalla ") || trimmed.startsWith("analiza pantalla:") ||
            trimmed == "que ves en mi pantalla" || trimmed.startsWith("que ves en mi pantalla ") || trimmed.startsWith("que ves en mi pantalla:") ||
            trimmed == "analizar mi pantalla" || trimmed.startsWith("analizar mi pantalla ") || trimmed.startsWith("analizar mi pantalla:") ||
            trimmed == "analizar pantalla" || trimmed.startsWith("analizar pantalla ") || trimmed.startsWith("analizar pantalla:") -> null

            // 3. Temporizador
            (trimmed.contains("temporizador") || trimmed.startsWith("pasa ")) &&
                (durationUnitRegex.containsMatchIn(trimmed) ||
                    durationSpokenUnitRegex.containsMatchIn(trimmed) ||
                    durationSpokenFractionUnitRegex.containsMatchIn(trimmed)) -> {
                val minutes = parseDurationMinutes(trimmed) ?: return null
                if (minutes !in SystemCommand.TIMER_MIN_MINUTOS..SystemCommand.TIMER_MAX_MINUTOS) {
                    return null // Fuera de rango → null (fallback a Gemini)
                }
                SystemCommand.SetTimer(minutes)
            }

            // 4a. Llamada con "al"
            trimmed.startsWith("llama al ") || trimmed.startsWith("llamar al ") -> {
                val arg = extractAfterPrefix(trimmed, text, "llama al ", "llamar al ")
                parseCallOrNumber(arg)
            }

            // 4b. Llamada a contacto o número
            trimmed.startsWith("llama a ") || trimmed.startsWith("llamar a ") -> {
                val arg = extractAfterPrefix(trimmed, text, "llama a ", "llamar a ")
                parseCallOrNumber(arg)
            }

            // 5. Volumen
            trimmed.startsWith("sube el volumen") || trimmed.startsWith("sube volumen") ||
                trimmed.startsWith("baja el volumen") || trimmed.startsWith("baja volumen") ||
                trimmed == "silencio" || trimmed.startsWith("silencio ") ||
                trimmed == "silencia" || trimmed.startsWith("silencia ") -> {
                val action = when {
                    trimmed.contains("maximo") -> com.screenassistant.core.domain.model.VolumeAction.MAX
                    trimmed.contains("minimo") -> com.screenassistant.core.domain.model.VolumeAction.MIN
                    trimmed.startsWith("sube") -> com.screenassistant.core.domain.model.VolumeAction.UP
                    trimmed.startsWith("baja") -> com.screenassistant.core.domain.model.VolumeAction.DOWN
                    else -> com.screenassistant.core.domain.model.VolumeAction.MUTE
                }
                SystemCommand.SetVolume(action)
            }

            // 6. Idioma
            (trimmed.startsWith("habla en ") || trimmed.startsWith("cambia a ")) &&
                (trimmed.contains("ingles") || trimmed.contains("espanol")) -> {
                val language = if (trimmed.contains("ingles")) {
                    com.screenassistant.core.domain.model.AssistantLanguage.ENGLISH
                } else {
                    com.screenassistant.core.domain.model.AssistantLanguage.SPANISH
                }
                SystemCommand.SetLanguage(language)
            }

            // 7. Ajustes
            trimmed.startsWith("abre los ajustes") || trimmed.startsWith("abre la configuracion") ||
                trimmed.startsWith("abre ajustes") || trimmed.startsWith("abre configuracion") ||
                trimmed.startsWith("abrir los ajustes") || trimmed.startsWith("abrir la configuracion") -> {
                SystemCommand.OpenSettings
            }

            // 8. Abrir app
            trimmed.startsWith("abre ") || trimmed.startsWith("abrir ") -> {
                val query = extractAfterPrefix(trimmed, text, "abre ", "abrir ")
                SystemCommand.OpenApp(query)
            }

            // 9. Navegación
            trimmed.startsWith("llevame a ") || trimmed.startsWith("navega a ") -> {
                val destination = extractAfterPrefix(trimmed, text, "llevame a ", "navega a ")
                SystemCommand.Navigate(destination)
            }

            // 10. Búsqueda en Google
            trimmed.startsWith("busca ") || trimmed.startsWith("buscar ") ||
                trimmed.startsWith("busca:") || trimmed.startsWith("buscar:") -> {
                val query = extractAfterPrefix(trimmed, text, "busca ", "buscar ", "busca:", "buscar:")
                    .trim().trimStart(':').trim()
                SystemCommand.SearchGoogle(query)
            }

            // 11. Memoria offline
            trimmed.startsWith("recuerda que ") || trimmed.startsWith("recuerda ") -> {
                val fact = extractAfterPrefix(trimmed, text, "recuerda que ", "recuerda ")
                SystemCommand.SaveMemory(fact)
            }

            // 11b. Cancelar alarma
            cancelAlarmRegex.containsMatchIn(trimmed) -> {
                val match = TimePhraseParser.findMatch(trimmed.substringAfter("alarma"))
                SystemCommand.CancelAlarm(match?.time?.hour, match?.time?.minute)
            }

            // 12. Alarma
            trimmed.contains("alarma") -> {
                val base = trimmed.substringAfter("alarma")
                val match = TimePhraseParser.findMatch(base)
                if (match != null) {
                    val paraIdx = base.indexOf("para ", match.matchEnd)
                    val label = if (paraIdx >= 0) {
                        text.substringAfter("alarma").substring(paraIdx + 5).trim().ifBlank { null }
                    } else {
                        null
                    }
                    SystemCommand.SetAlarm(match.time.hour, match.time.minute, label)
                } else {
                    SystemCommand.OpenAlarms
                }
            }

            // ===== Fase 2: Capacidades offline =====

            // 13. Bluetooth
            trimmed.startsWith("activa el bluetooth") || trimmed.startsWith("activar bluetooth") ||
            trimmed.startsWith("enciende el bluetooth") || trimmed.startsWith("enciende bluetooth") -> {
                SystemCommand.SetBluetooth(true)
            }
            trimmed.startsWith("desactiva el bluetooth") || trimmed.startsWith("desactivar bluetooth") ||
            trimmed.startsWith("apaga el bluetooth") || trimmed.startsWith("apaga bluetooth") -> {
                SystemCommand.SetBluetooth(false)
            }

            // 14. Brillo
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
                    SystemCommand.SetBrightness(level)
                } else {
                    null // Fuera de rango → Gemini
                }
            }
            trimmed.startsWith("sube el brillo") || trimmed.startsWith("subir brillo") ||
            trimmed.startsWith("aumenta el brillo") -> {
                SystemCommand.SetBrightness(-1) // -1 = subir
            }
            trimmed.startsWith("baja el brillo") || trimmed.startsWith("bajar brillo") ||
            trimmed.startsWith("reduce el brillo") -> {
                SystemCommand.SetBrightness(-2) // -2 = bajar
            }

            // 15. Linterna
            trimmed.startsWith("enciende la linterna") || trimmed.startsWith("activa la linterna") ||
            trimmed.startsWith("encender linterna") || trimmed.startsWith("activar linterna") ||
            trimmed.startsWith("prende la linterna") || trimmed == "linterna" -> {
                SystemCommand.SetFlashlight(true)
            }
            trimmed.startsWith("apaga la linterna") || trimmed.startsWith("desactiva la linterna") ||
            trimmed.startsWith("apagar linterna") || trimmed.startsWith("desactivar linterna") ||
            trimmed.startsWith("apaga linterna") -> {
                SystemCommand.SetFlashlight(false)
            }

            // 16. Modo avión
            trimmed.startsWith("activa el modo avion") || trimmed.startsWith("activar modo avion") ||
            trimmed.startsWith("enciende modo avion") -> {
                SystemCommand.SetAirplaneMode(true)
            }
            trimmed.startsWith("desactiva el modo avion") || trimmed.startsWith("desactivar modo avion") ||
            trimmed.startsWith("apaga modo avion") -> {
                SystemCommand.SetAirplaneMode(false)
            }

            // 17. Datos móviles
            trimmed.startsWith("activa los datos moviles") || trimmed.startsWith("activar datos moviles") ||
            trimmed.startsWith("enciende los datos") || trimmed.startsWith("activa datos moviles") -> {
                SystemCommand.SetMobileData(true)
            }
            trimmed.startsWith("desactiva los datos moviles") || trimmed.startsWith("desactivar datos moviles") ||
            trimmed.startsWith("apaga los datos") || trimmed.startsWith("desactiva datos moviles") -> {
                SystemCommand.SetMobileData(false)
            }

            // 18. Info WiFi
            trimmed.startsWith("que wifi tengo") || trimmed.startsWith("que red tengo") ||
            trimmed.startsWith("nombre de mi wifi") || trimmed.startsWith("nombre de mi red") ||
            trimmed.startsWith("info del wifi") || trimmed.startsWith("informacion del wifi") -> {
                SystemCommand.GetWifiInfo
            }

            // 19. Info del dispositivo
            trimmed.startsWith("que telefono tengo") || trimmed.startsWith("que celular tengo") ||
            trimmed.startsWith("que modelo es mi telefono") -> {
                SystemCommand.DeviceInfo(com.screenassistant.core.domain.model.DeviceInfoType.MODEL)
            }
            trimmed.startsWith("cuanta bateria queda") || trimmed.startsWith("cuanto bateria queda") ||
            trimmed.startsWith("nivel de bateria") -> {
                SystemCommand.DeviceInfo(com.screenassistant.core.domain.model.DeviceInfoType.BATTERY)
            }
            trimmed.startsWith("cuanto espacio libre") || trimmed.startsWith("cuanto espacio tengo") ||
            trimmed.startsWith("espacio libre") -> {
                SystemCommand.DeviceInfo(com.screenassistant.core.domain.model.DeviceInfoType.STORAGE)
            }

            // 20. Portapapeles
            trimmed == "copia" || trimmed == "copiar" || trimmed.startsWith("copia ") || trimmed.startsWith("copiar ") -> {
                val textToCopy = if (trimmed == "copia" || trimmed == "copiar") "" else extractAfterPrefix(trimmed, text, "copia ", "copiar ")
                if (textToCopy.isEmpty()) null else SystemCommand.Clipboard(com.screenassistant.core.domain.model.ClipboardOperation.COPY, textToCopy)
            }
            trimmed == "pega" || trimmed == "pegar" || trimmed.startsWith("pega ") || trimmed.startsWith("pegar ") -> {
                SystemCommand.Clipboard(com.screenassistant.core.domain.model.ClipboardOperation.PASTE)
            }
            trimmed.startsWith("que tengo copiado") || trimmed.startsWith("que hay en el portapapeles") -> {
                SystemCommand.Clipboard(com.screenassistant.core.domain.model.ClipboardOperation.SHOW)
            }

            // 21. Cronómetro
            trimmed.startsWith("inicia cronometro") || trimmed.startsWith("arranca cronometro") ||
            trimmed.startsWith("empieza cronometro") || trimmed.startsWith("iniciar cronometro") -> {
                SystemCommand.Stopwatch(com.screenassistant.core.domain.model.StopwatchAction.START)
            }
            trimmed.startsWith("para el cronometro") || trimmed.startsWith("para cronometro") ||
            trimmed.startsWith("deten cronometro") || trimmed.startsWith("parar cronometro") -> {
                SystemCommand.Stopwatch(com.screenassistant.core.domain.model.StopwatchAction.STOP)
            }
            trimmed.startsWith("cuanto tiempo lleva") || trimmed.startsWith("cuanto va el cronometro") ||
            trimmed == "cronometro" -> {
                SystemCommand.Stopwatch(com.screenassistant.core.domain.model.StopwatchAction.GET_TIME)
            }

            // 22. Listar contactos
            trimmed.startsWith("lista mis contactos") || trimmed.startsWith("listar mis contactos") ||
            trimmed.startsWith("lista contactos") || trimmed.startsWith("mostrar contactos") -> {
                SystemCommand.ListContacts
            }

            // 23. Abrir archivo
            trimmed.startsWith("abre el archivo ") || trimmed.startsWith("abrir archivo ") ||
            trimmed.startsWith("abre archivo ") -> {
                val query = extractAfterPrefix(trimmed, text, "abre el archivo ", "abrir archivo ", "abre archivo ")
                SystemCommand.OpenFile(query)
            }

            // 24. Historial de llamadas
            trimmed.startsWith("historial de llamadas") || trimmed.startsWith("llamadas recientes") ||
            trimmed == "llamadas" -> {
                SystemCommand.CallHistory
            }

            // ===== Fase 3: Capacidades offline con ML Kit =====

            // 25. QR
            trimmed.startsWith("escanea codigo qr") || trimmed.startsWith("escanear codigo qr") ||
            trimmed.startsWith("escanea qr") || trimmed == "codigo qr" -> {
                SystemCommand.ScanQr
            }

            // 26. OCR
            trimmed.startsWith("lee el texto de la pantalla") || trimmed.startsWith("leer texto de la pantalla") ||
            trimmed == "ocr" || trimmed.startsWith("lee la pantalla") -> {
                SystemCommand.OcrScan
            }

            // 27. Traducción
            trimmed.startsWith("traduce ") || trimmed.startsWith("traducir ") -> {
                val raw = extractAfterPrefix(trimmed, text, "traduce ", "traducir ")
                val targetLang = when {
                    raw.contains("al ingles") || raw.contains("a english") ->
                        com.screenassistant.core.domain.model.TranslateLanguage.ENGLISH
                    raw.contains("al frances") || raw.contains("a francais") ->
                        com.screenassistant.core.domain.model.TranslateLanguage.FRENCH
                    raw.contains("al aleman") || raw.contains("a deutsch") ->
                        com.screenassistant.core.domain.model.TranslateLanguage.GERMAN
                    raw.contains("al portugues") || raw.contains("a portugues") ->
                        com.screenassistant.core.domain.model.TranslateLanguage.PORTUGUESE
                    raw.contains("al chino") || raw.contains("a chino") ->
                        com.screenassistant.core.domain.model.TranslateLanguage.CHINESE
                    raw.contains("al japones") || raw.contains("a japones") ->
                        com.screenassistant.core.domain.model.TranslateLanguage.JAPANESE
                    else -> com.screenassistant.core.domain.model.TranslateLanguage.ENGLISH
                }
                val textToTranslate = raw
                    .replace(Regex("""\s*al?\s+(?:ingles|english|frances|francais|aleman|deutsch|portugues|chino|japones)\s*"""), "")
                    .trim()
                if (textToTranslate.isEmpty()) null else SystemCommand.TranslateText(textToTranslate, targetLang)
            }

            // 28. Reconocimiento facial
            trimmed == "reconocimiento facial" || trimmed == "cara" ||
            trimmed.startsWith("detecta caras") || trimmed.startsWith("detectar caras") ||
            trimmed.startsWith("hay caras") || trimmed.startsWith("reconoce caras") -> {
                SystemCommand.DetectFace
            }

            // ===== Fase 4: Hardware directo =====

            // 29. Vibración
            trimmed.startsWith("vibra ") || trimmed.startsWith("vibrar ") ||
            trimmed == "vibra" || trimmed == "vibrar" -> {
                val arg = if (trimmed == "vibra" || trimmed == "vibrar") "" else
                    trimmed.removePrefix("vibra ").removePrefix("vibrar ").trim()
                parseVibration(arg)
            }

            // 30. Ubicación GPS
            trimmed.startsWith("donde estoy") || trimmed.startsWith("dónde estoy") ||
            trimmed.startsWith("mi ubicacion") || trimmed.startsWith("mi ubicación") ||
            trimmed == "ubicacion" || trimmed == "ubicación" ||
            trimmed.startsWith("coordenadas") || trimmed.startsWith("donde me encuentro") -> {
                SystemCommand.GetLocation
            }

            // 31. WiFi toggle
            trimmed.startsWith("enciende el wifi") || trimmed.startsWith("encender wifi") ||
            trimmed.startsWith("activa el wifi") || trimmed.startsWith("activar wifi") ||
            trimmed == "wifi" -> {
                SystemCommand.SetWifi(true)
            }
            trimmed.startsWith("apaga el wifi") || trimmed.startsWith("apagar wifi") ||
            trimmed.startsWith("desactiva el wifi") || trimmed.startsWith("desactivar wifi") -> {
                SystemCommand.SetWifi(false)
            }

            // 32. Captura de cámara
            trimmed.startsWith("saca una foto") || trimmed.startsWith("tomar foto") ||
            trimmed.startsWith("toma una foto") || trimmed.startsWith("sacar foto") ||
            trimmed == "foto" || trimmed == "captura" || trimmed == "selfie" -> {
                val useFront = trimmed.contains("frontal") || trimmed.contains("delantera") ||
                    trimmed.contains("selfie")
                SystemCommand.TakePhoto(useFront)
            }

            else -> null // No es un comando directo
        }
    }

    // ===== Lógica de parsing compartida (copiada de SystemCommandParser para independencia) =====

    private val phoneRegex = Regex("""\+?\d[\d\s\-.]*""")
    private val UNIT_PATTERN = "horas?|minutos?|segundos?"
    private val durationUnitRegex = Regex("""(\d+)\s*($UNIT_PATTERN)""")
    private val durationSpokenUnitRegex = Regex(
        """(${com.screenassistant.core.domain.usecase.SpokenNumber.quantityTokens().joinToString("|")})(?:\s+y\s+(${com.screenassistant.core.domain.usecase.SpokenNumber.unitTokens().joinToString("|")}))?\s+($UNIT_PATTERN)"""
    )
    private val durationSpokenFractionUnitRegex = Regex("""(media|cuarto)\s+(?:de\s+)?horas?""")
    private val durationFractionRegex = Regex(
        """(?:(?:\d+)|(?:${com.screenassistant.core.domain.usecase.SpokenNumber.quantityTokens().joinToString("|")}))\s+horas?\s+y\s+(media|cuarto)(?!\s+(?:de\s+)?horas?)"""
    )
    private val durationBareRegex = Regex(
        """(?:(?:\d+)|(?:${com.screenassistant.core.domain.usecase.SpokenNumber.quantityTokens().joinToString("|")}))\s*(horas?|minutos?)\s+y\s+(\d+)(?!\s*(?:\d|(?:$UNIT_PATTERN)))"""
    )
    private val minuteFractionResidualRegex = Regex(
        """(?:\d+|${com.screenassistant.core.domain.usecase.SpokenNumber.quantityTokens().joinToString("|")})\s+minutos?\s+y\s+(?:media|medio|cuarto)(?!\s+horas?)"""
    )
    private val unsupportedTensRegex = Regex(
        """\b(?:sesenta|setenta|ochenta|noventa|cien|ciento)\s+y\s+(?:\d+|[a-z]+)\s+(?:$UNIT_PATTERN)"""
    )
    private val invalidCompoundRegex = Regex(
        """\b(?:veinte|treinta|cuarenta|cincuenta)\s+y\s+(?!(?:${com.screenassistant.core.domain.usecase.SpokenNumber.unitTokens().joinToString("|")})|(?:[1-9](?![0-9])))(?:\d+|[a-z]+)\s+(?:$UNIT_PATTERN)"""
    )
    private val cancelAlarmRegex = Regex(
        """^(cancela|cancelar|quita|quitar|elimina|eliminar)\s+(la\s+alarma|las\s+alarmas|mi\s+alarma|mis\s+alarmas)"""
    )

    private val HELP_PHRASES = listOf(
        "que puedes hacer", "que me puedo ayudar", "que puedo hacer",
        "me puedes ayudar", "me puede ayudar", "que puedes ayudarme",
        "que sabes hacer", "que comandos tienes", "para que sirves"
    )

    private val NOTE_PREFIXES = listOf(
        "crear una nota de ", "crear una nota de:", "crear una nota del ", "crear una nota: ", "crear una nota:", "crear una nota ",
        "crea una nota de ", "crea una nota de:", "crea una nota del ", "crea una nota: ", "crea una nota:", "crea una nota ",
        "escribir una nota de ", "escribir una nota de:", "escribir una nota del ", "escribir una nota: ", "escribir una nota:", "escribir una nota ",
        "escribe una nota de ", "escribe una nota de:", "escribe una nota del ", "escribe una nota: ", "escribe una nota:", "escribe una nota ",
        "anota: ", "anota:", "anota ",
        "toma nota de ", "toma nota de:", "toma nota del ", "tomar nota de ", "tomar nota de:", "tomar nota del ",
        "toma nota: ", "tomar nota: ",
        "toma nota:", "tomar nota:", "toma nota ", "tomar nota "
    )

    private val cortesiaSufijoRegex = Regex("""(?i)\s*(?:por\s+favor|porfavor|porfa)\s*$""")

    private fun parseCallOrNumber(arg: String): SystemCommand? {
        val limpio = cortesiaSufijoRegex.replace(arg.trim(), "").trim()
        if (limpio.isEmpty()) return null
        return if (limpio.matches(phoneRegex)) {
            SystemCommand.CallNumber(limpio.replace(Regex("""[.\s-]"""), ""))
        } else {
            SystemCommand.Call(limpio)
        }
    }

    private fun parseDurationMinutes(trimmed: String): Int? {
        var numMinutes = 0
        var hasSeconds = false
        for (m in durationUnitRegex.findAll(trimmed)) {
            when {
                m.groupValues[2].startsWith("hora") -> numMinutes += m.groupValues[1].toInt() * 60
                m.groupValues[2].startsWith("minuto") -> numMinutes += m.groupValues[1].toInt()
                else -> hasSeconds = true
            }
        }
        var spokenMinutes = 0
        for (m in durationSpokenUnitRegex.findAll(trimmed)) {
            val quantity = com.screenassistant.core.domain.usecase.SpokenNumber.resolve(
                m.groupValues[1], m.groupValues[2].takeIf { it.isNotEmpty() }
            ) ?: return null
            when {
                m.groupValues[3].startsWith("hora") -> spokenMinutes += quantity * 60
                m.groupValues[3].startsWith("minuto") -> spokenMinutes += quantity
                else -> hasSeconds = true
            }
        }
        val fraction = durationFractionRegex.findAll(trimmed).map { m ->
            if (m.groupValues[1] == "media") 30 else 15
        }.sum()
        val fractionUnit = durationSpokenFractionUnitRegex.find(trimmed)
            ?.let { if (it.groupValues[1] == "media") 30 else 15 } ?: 0
        val bare = durationBareRegex.find(trimmed)?.let { m ->
            val n = m.groupValues[2].toInt()
            if (n !in 0..59) return null else n
        } ?: 0
        if (minuteFractionResidualRegex.containsMatchIn(trimmed) ||
            unsupportedTensRegex.containsMatchIn(trimmed) ||
            invalidCompoundRegex.containsMatchIn(trimmed)
        ) return null
        val ceilSeconds = if (numMinutes == 0 && spokenMinutes == 0 && hasSeconds) 1 else 0
        return numMinutes + spokenMinutes + fraction + fractionUnit + bare + ceilSeconds
    }

    /**
     * Normalización para detección de conectores: lowercase, quita tildes, signos de puntuación.
     * PRESERVA `,` y `;` para que los patrones COMA_LUEGO/PUNTOCOMA_LUEGO matcheen.
     */
    private fun normalizeForConnectors(text: String): String =
        text.lowercase()
            .replace('á', 'a').replace('é', 'e').replace('í', 'i')
            .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')
            .replace('ñ', 'n')
            .replace('¿', ' ').replace('¡', ' ').replace('?', ' ').replace('!', ' ')
            .replace('.', ' ')

    /** Normalización para parseCommand: igual que SystemCommandParser.normalize */
    private fun normalize(text: String): String =
        normalizeForConnectors(text).replace(',', ' ').replace(';', ' ')

    private fun extractAfterPrefix(trimmed: String, original: String, vararg prefixes: String): String {
        val prefix = prefixes.first { trimmed.trim().startsWith(it) }
        return original.trim().substring(prefix.length)
            .trim()
            .trimStart(' ', '.', ',', ';', '?', '!', '¿', '¡', ':')
            .trimEnd(' ', '.', ',', ';', '?', '!', '¿', '¡', ':')
    }

    private fun cleanNoteText(raw: String): String =
        raw.trim()
            .trimStart(' ', '.', ',', ';', '?', '!', '¿', '¡', ':')
            .trimEnd(' ', '.', ',', ';', '?', '!', '¿', '¡', ':')

    /**
     * Parsea el argumento de vibración y retorna SystemCommand.Vibrate o null.
     * Patrones conocidos: sos, llamada, alarma → durationMs = 0 (el action decide).
     * Números puros → milisegundos directos. "2 segundos" → 2000ms.
     */
    private fun parseVibration(arg: String): SystemCommand? {
        val trimmedArg = arg.trim().lowercase()
        if (trimmedArg.isEmpty()) {
            return SystemCommand.Vibrate(500) // Default: 500ms
        }
        // Patrones conocidos
        val patterns = listOf("sos", "llamada", "alarma")
        if (trimmedArg in patterns) {
            return SystemCommand.Vibrate(0) // El action maneja patrones por nombre
        }
        // Extraer número: "2 segundos", "500 ms", "1.5 segundos", "500"
        val numberRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:segundos?|s|ms|milisegundos?)?""")
        val match = numberRegex.find(trimmedArg)
        if (match != null) {
            val value = match.groupValues[1].toDoubleOrNull() ?: return null
            val durationMs = if (trimmedArg.contains("ms") || trimmedArg.contains("milisegundos")) {
                value.toLong()
            } else {
                (value * 1000).toLong() // Convertir segundos a ms
            }
            return SystemCommand.Vibrate(durationMs)
        }
        return null // Argumento no reconocido → null (fallback a Gemini)
    }
}