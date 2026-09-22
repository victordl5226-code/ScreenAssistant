package com.screenassistant.core.nlp.classifier

import com.screenassistant.core.domain.nlp.IntentClassifier
import com.screenassistant.core.domain.nlp.model.NlpIntent

/**
 * Clasificador basado en reglas deterministas.
 *
 * Estrategia: lista ordenada de reglas. La primera que coincide gana.
 * Cada regla tiene: patrones (regex), intención, confianza base.
 *
 * Rendimiento: O(n) donde n = número de reglas (~40).
 * En benchmarks típicos: < 5ms para 56+ comandos.
 */
class RuleBasedIntentClassifier(
    private val reglas: List<IntentRule> = reglasPorDefecto()
) : IntentClassifier {

    override fun clasificar(textoNormalizado: String): Pair<NlpIntent, Double> {
        for (regla in reglas) {
            if (regla.coincide(textoNormalizado)) {
                return regla.intencion to regla.confianzaBase
            }
        }
        return NlpIntent.UNKNOWN to 0.0
    }

    companion object {
        /** Retorna la lista por defecto de reglas de clasificación. */
        fun reglasPorDefecto(): List<IntentRule> = listOf(

            // === Notas (precedencia máxima) ===
            IntentRule(
                patrones = listOf(
                    Regex("""^(?:crear|crea|escribir|escribe|anota|toma nota)\s+una\s+nota"""),
                    Regex("""^(?:anota|toma nota)\s*[ :]""")
                ),
                intencion = NlpIntent.CREATE_NOTE,
                confianzaBase = 0.95
            ),
            IntentRule(
                patrones = listOf(
                    Regex("""^lee\s+mis\s+notas"""),
                    Regex("""^lea\s+mis\s+notas""")
                ),
                intencion = NlpIntent.READ_ALL_NOTES,
                confianzaBase = 0.98
            ),
            IntentRule(
                patrones = listOf(
                    Regex("""^lee\s+la\s+nota\s+(?:de|del|:|\s)""")
                ),
                intencion = NlpIntent.READ_NOTE,
                confianzaBase = 0.95
            ),

            // === Ayuda ===
            IntentRule(
                patrones = listOf(
                    Regex("""(?:que puedes hacer|que puedo hacer|me puedes ayudar|que sabes hacer|que comandos tienes|para que sirves)"""),
                    Regex("""^(?:ayuda|comandos)$""")
                ),
                intencion = NlpIntent.HELP,
                confianzaBase = 0.95
            ),

            // === Repite ===
            IntentRule(
                patrones = listOf(
                    Regex("""^(?:repite|otra vez)$""")
                ),
                intencion = NlpIntent.REPEAT,
                confianzaBase = 0.95
            ),

            // === Llamadas ===
            IntentRule(
                patrones = listOf(
                    Regex("""^(?:llama|llamar)\s+(?:al)\s+\+?\d"""),
                    Regex("""^(?:llama|llamar)\s+(?:al)\s+\d[\d\s\-.]*$""")
                ),
                intencion = NlpIntent.CALL_NUMBER,
                confianzaBase = 0.90
            ),
            IntentRule(
                patrones = listOf(
                    Regex("""^(?:llama|llamar)\s+(?:al|a)\s+""")
                ),
                intencion = NlpIntent.CALL_CONTACT,
                confianzaBase = 0.88
            ),
            IntentRule(
                patrones = listOf(
                    Regex("""^lista\s+(?:mis\s+)?contactos?$""")
                ),
                intencion = NlpIntent.LIST_CONTACTS,
                confianzaBase = 0.90
            ),
            IntentRule(
                patrones = listOf(
                    Regex("""^historial\s+de\s+llamadas?$""")
                ),
                intencion = NlpIntent.CALL_HISTORY,
                confianzaBase = 0.92
            ),

            // === Alarmas ===
            IntentRule(
                patrones = listOf(
                    Regex("""(?:cancela|cancelar|quita|quitar|elimina|eliminar)\s+(?:la|las|mi|mis)\s+alarma""")
                ),
                intencion = NlpIntent.CANCEL_ALARM,
                confianzaBase = 0.92
            ),
            IntentRule(
                patrones = listOf(
                    Regex("""(?:alarmas?|despiertame|despertador)""")
                ),
                intencion = NlpIntent.SET_ALARM,
                confianzaBase = 0.80
            ),

            // === Temporizador ===
            IntentRule(
                patrones = listOf(
                    Regex("""(?:temporizador|cronometro)\s+de\s+\d+"""),
                    Regex("""(?:temporizador|cronometro)\s+(?:de\s+)?(?:un|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|media|cuarto)""")
                ),
                intencion = NlpIntent.SET_TIMER,
                confianzaBase = 0.85
            ),

            // === Volumen ===
            IntentRule(
                patrones = listOf(
                    Regex("""(?:sube|baja|subir|bajar)\s+(?:el\s+)?volumen"""),
                    Regex("""(?:silencio|silencia|mutear|mutear)""")
                ),
                intencion = NlpIntent.SET_VOLUME,
                confianzaBase = 0.92
            ),

            // === Apps ===
            IntentRule(
                patrones = listOf(
                    Regex("""^(?:abre|abrir)\s+(?:los?\s+)?(?:ajustes|configuracion|configuración)""")
                ),
                intencion = NlpIntent.OPEN_SETTINGS,
                confianzaBase = 0.95
            ),
            IntentRule(
                patrones = listOf(
                    Regex("""^(?:abre|abrir)\s+(?!el\s+archivo|archivo|ajustes)""")
                ),
                intencion = NlpIntent.OPEN_APP,
                confianzaBase = 0.85
            ),

            // === Búsqueda ===
            IntentRule(
                patrones = listOf(
                    Regex("""^(?:busca|buscar)\s+""")
                ),
                intencion = NlpIntent.SEARCH_GOOGLE,
                confianzaBase = 0.92
            ),

            // === Memoria ===
            IntentRule(
                patrones = listOf(
                    Regex("""^recuerda(?:\s+que)?\s+""")
                ),
                intencion = NlpIntent.SAVE_MEMORY,
                confianzaBase = 0.90
            ),

            // === Navegación ===
            IntentRule(
                patrones = listOf(
                    Regex("""^(?:llevame|llévame|navega)\s+a\s+""")
                ),
                intencion = NlpIntent.NAVIGATE,
                confianzaBase = 0.90
            ),

            // === Bluetooth ===
            IntentRule(
                patrones = listOf(
                    Regex("""(?:activa|enciende|encender|abre)\s+(?:el\s+)?bluetooth"""),
                    Regex("""(?:desactiva|apaga|apagar|cierra)\s+(?:el\s+)?bluetooth""")
                ),
                intencion = NlpIntent.SET_BLUETOOTH,
                confianzaBase = 0.92
            ),

            // === Brillo ===
            IntentRule(
                patrones = listOf(
                    Regex("""(?:pon|ajusta)\s+(?:el\s+)?brillo\s+(?:a|al)\s+"""),
                    Regex("""(?:sube|baja|subir|bajar|aumenta|reduce)\s+(?:el\s+)?brillo""")
                ),
                intencion = NlpIntent.SET_BRIGHTNESS,
                confianzaBase = 0.90
            ),

            // === Linterna ===
            IntentRule(
                patrones = listOf(
                    Regex("""(?:enciende|activa|prende)\s+(?:la\s+)?linterna"""),
                    Regex("""(?:apaga|desactiva)\s+(?:la\s+)?linterna"""),
                    Regex("""^linterna$""")
                ),
                intencion = NlpIntent.SET_FLASHLIGHT,
                confianzaBase = 0.92
            ),

            // === Modo avión ===
            IntentRule(
                patrones = listOf(
                    Regex("""(?:enciende|activa|prende|pon)\s+(?:el\s+)?modo\s+avion"""),
                    Regex("""(?:desactiva|apaga|quita)\s+(?:el\s+)?modo\s+avion""")
                ),
                intencion = NlpIntent.SET_AIRPLANE_MODE,
                confianzaBase = 0.90
            ),

            // === Datos móviles ===
            IntentRule(
                patrones = listOf(
                    Regex("""(?:activa|enciende)\s+(?:los\s+)?datos"""),
                    Regex("""(?:desactiva|apaga)\s+(?:los\s+)?datos""")
                ),
                intencion = NlpIntent.SET_MOBILE_DATA,
                confianzaBase = 0.88
            ),

            // === WiFi ===
            IntentRule(
                patrones = listOf(
                    Regex("""(?:enciende|activa|prende)\s+(?:el\s+)?wifi"""),
                    Regex("""(?:apaga|desactiva|cierra)\s+(?:el\s+)?wifi"""),
                    Regex("""^wifi$""")
                ),
                intencion = NlpIntent.SET_WIFI,
                confianzaBase = 0.90
            ),

            // === Vibración ===
            IntentRule(
                patrones = listOf(
                    Regex("""(?:vibra|vibrar)""")
                ),
                intencion = NlpIntent.VIBRATE,
                confianzaBase = 0.85
            ),

            // === Ubicación ===
            IntentRule(
                patrones = listOf(
                    Regex("""(?:donde\s+estoy|mi\s+ubicacion|coordenadas|latitud)""")
                ),
                intencion = NlpIntent.GET_LOCATION,
                confianzaBase = 0.90
            ),

            // === Foto ===
            IntentRule(
                patrones = listOf(
                    Regex("""(?:saca|toma|tomar|sacar)\s+(?:una\s+)?foto"""),
                    Regex("""^(?:foto|captura|selfie)$""")
                ),
                intencion = NlpIntent.TAKE_PHOTO,
                confianzaBase = 0.90
            ),

            // === Dispositivo ===
            IntentRule(
                patrones = listOf(
                    Regex("""(?:que\s+(?:telefono|celular)\s+(?:tengo|es|modelo))""")
                ),
                intencion = NlpIntent.DEVICE_INFO_MODEL,
                confianzaBase = 0.88
            ),
            IntentRule(
                patrones = listOf(
                    Regex("""(?:cuanta|cuanto)\s+bateria\s+(?:queda|tengo)"""),
                    Regex("""nivel\s+de\s+bateria""")
                ),
                intencion = NlpIntent.DEVICE_INFO_BATTERY,
                confianzaBase = 0.88
            ),
            IntentRule(
                patrones = listOf(
                    Regex("""(?:cuanto\s+(?:espacio|almacenamiento)\s+(?:libre|queda|tengo))""")
                ),
                intencion = NlpIntent.DEVICE_INFO_STORAGE,
                confianzaBase = 0.88
            ),
            IntentRule(
                patrones = listOf(
                    Regex("""^wifi\s+(?:info|informacion|datos)$"""),
                    Regex("""(?:que\s+wifi|wifi\s+que\s+tengo)""")
                ),
                intencion = NlpIntent.GET_WIFI_INFO,
                confianzaBase = 0.85
            ),

            // === Clipboard ===
            IntentRule(
                patrones = listOf(
                    Regex("""^(?:copia|copiar)\s+"""),
                    Regex("""^(?:copia|copiar)$""")
                ),
                intencion = NlpIntent.CLIPBOARD_COPY,
                confianzaBase = 0.85
            ),
            IntentRule(
                patrones = listOf(
                    Regex("""^(?:pega|pegar)$""")
                ),
                intencion = NlpIntent.CLIPBOARD_PASTE,
                confianzaBase = 0.88
            ),
            IntentRule(
                patrones = listOf(
                    Regex("""^(?:portapapeles|que\s+tengo\s+copiado)$""")
                ),
                intencion = NlpIntent.CLIPBOARD_SHOW,
                confianzaBase = 0.85
            ),

            // === Calculadora ===
            IntentRule(
                patrones = listOf(
                    Regex("""(?:cuanto\s+es|que\s+es)\s+\d"""),
                    Regex("""(?:suma|resta|multiplica|divide)\s+\d""")
                ),
                intencion = NlpIntent.CALCULATOR,
                confianzaBase = 0.80
            ),

            // === Cronómetro ===
            IntentRule(
                patrones = listOf(
                    Regex("""(?:inicia|arranca|empieza|iniciar)\s+(?:el\s+)?cronometro""")
                ),
                intencion = NlpIntent.STOPWATCH_START,
                confianzaBase = 0.90
            ),
            IntentRule(
                patrones = listOf(
                    Regex("""(?:para|deten|detener|parar)\s+(?:el\s+)?cronometro""")
                ),
                intencion = NlpIntent.STOPWATCH_STOP,
                confianzaBase = 0.90
            ),
            IntentRule(
                patrones = listOf(
                    Regex("""(?:que\s+hora\s+lleva|tiempo\s+del)\s+(?:el\s+)?cronometro""")
                ),
                intencion = NlpIntent.STOPWATCH_GET_TIME,
                confianzaBase = 0.88
            ),

            // === Monitoreo ===
            IntentRule(
                patrones = listOf(
                    Regex("""(?:activar|iniciar|encender)\s+monitoreo""")
                ),
                intencion = NlpIntent.START_MONITORING,
                confianzaBase = 0.92
            ),
            IntentRule(
                patrones = listOf(
                    Regex("""(?:detener|parar|apagar)\s+monitoreo""")
                ),
                intencion = NlpIntent.STOP_MONITORING,
                confianzaBase = 0.92
            ),

            // === Pantalla ===
            IntentRule(
                patrones = listOf(
                    Regex("""(?:analiza|que\s+(?:hay|ves)\s+en)\s+(?:mi\s+)?pantalla""")
                ),
                intencion = NlpIntent.ANALYZE_SCREEN,
                confianzaBase = 0.90
            ),

            // === ML Kit ===
            IntentRule(
                patrones = listOf(
                    Regex("""(?:escanea|escanear|lee|leer)\s+(?:el\s+)?(?:codigo\s+)?qr""")
                ),
                intencion = NlpIntent.SCAN_QR,
                confianzaBase = 0.90
            ),
            IntentRule(
                patrones = listOf(
                    Regex("""(?:lee|leer)\s+(?:el\s+)?texto\s+de\s+la\s+pantalla"""),
                    Regex("""^ocr$""")
                ),
                intencion = NlpIntent.OCR_SCAN,
                confianzaBase = 0.88
            ),
            IntentRule(
                patrones = listOf(
                    Regex("""(?:traduce|traducir)\s+""")
                ),
                intencion = NlpIntent.TRANSLATE_TEXT,
                confianzaBase = 0.88
            ),
            IntentRule(
                patrones = listOf(
                    Regex("""(?:detecta|detectar|hay|reconoce)\s+caras?""")
                ),
                intencion = NlpIntent.DETECT_FACE,
                confianzaBase = 0.88
            ),

            // === SMS ===
            IntentRule(
                patrones = listOf(
                    Regex("""^(?:envia|enviar|manda|mandar)\s+(?:un\s+)?mensaje\s+a\s+""")
                ),
                intencion = NlpIntent.SEND_SMS,
                confianzaBase = 0.85
            ),

            // === Archivo ===
            IntentRule(
                patrones = listOf(
                    Regex("""^(?:abre|abrir)\s+(?:el\s+)?archivo\s+""")
                ),
                intencion = NlpIntent.OPEN_FILE,
                confianzaBase = 0.88
            ),

            // === Música ===
            IntentRule(
                patrones = listOf(
                    Regex("""(?:reproduce|reproducir|pon|poner|escuchar)\s+(?:la\s+)?musica?"""),
                    Regex("""^(?:musica|cancion|canciones)$""")
                ),
                intencion = NlpIntent.PLAY_MUSIC,
                confianzaBase = 0.82
            ),

            // === Idioma ===
            IntentRule(
                patrones = listOf(
                    Regex("""(?:habla|cambia|cambiar)\s+(?:al?\s+)?(?:idioma\s+)?(?:espanol|ingles|frances|aleman|portugues)""")
                ),
                intencion = NlpIntent.SET_LANGUAGE,
                confianzaBase = 0.85
            )
        )
    }
}
