package com.screenassistant.feature.overlay

/**
 * Responde localmente (sin nube) a preguntas del tipo
 * "¿qué puedes hacer?", "¿en qué me ayudas?", etc.
 *
 * Retorna un texto formateado con todas las categorías
 * de comandos que el asistente puede ejecutar.
 */
object LocalHelpResponder {

    /** Patrones que detectan preguntas de ayuda */
    private val HELP_PATTERNS = listOf(
        Regex("qué puedes hacer", RegexOption.IGNORE_CASE),
        Regex("que puedes hacer", RegexOption.IGNORE_CASE),
        Regex("qué puedes ayudarme", RegexOption.IGNORE_CASE),
        Regex("que puedes ayudarme", RegexOption.IGNORE_CASE),
        Regex("en qué puedo ayudarte", RegexOption.IGNORE_CASE),
        Regex("en que puedo ayudarte", RegexOption.IGNORE_CASE),
        Regex("qué me puedes ayudar", RegexOption.IGNORE_CASE),
        Regex("que me puedes ayudar", RegexOption.IGNORE_CASE),
        Regex("qué sabes hacer", RegexOption.IGNORE_CASE),
        Regex("que sabes hacer", RegexOption.IGNORE_CASE),
        Regex("qué comandos tienes", RegexOption.IGNORE_CASE),
        Regex("que comandos tienes", RegexOption.IGNORE_CASE),
        Regex("para qué sirves", RegexOption.IGNORE_CASE),
        Regex("para que sirves", RegexOption.IGNORE_CASE),
        Regex("ayuda", RegexOption.IGNORE_CASE),
        Regex("comandos", RegexOption.IGNORE_CASE),
        Regex("cuáles son tus comandos", RegexOption.IGNORE_CASE),
        Regex("cuales son tus comandos", RegexOption.IGNORE_CASE),
    )

    /**
     * Verifica si el mensaje es una pregunta de ayuda.
     */
    fun isHelpQuery(message: String): Boolean {
        val normalized = message.trim().lowercase()
        return HELP_PATTERNS.any { it.containsMatchIn(normalized) }
    }

    /**
     * Retorna la respuesta completa de ayuda con todos los comandos.
     */
    fun getHelpResponse(): String = buildString {
        appendLine("Puedo ayudarte con muchas cosas. Estos son mis comandos:")
        appendLine()

        // Llamadas y contactos
        appendLine("📞 LLAMADAS Y CONTACTOS")
        appendLine("• Llama a [nombre] — Llama a un contacto")
        appendLine("• Llama a [número] — Llama a un número")
        appendLine("• Lista mis contactos — Muestra tus contactos")
        appendLine("• Historial de llamadas — Llamadas recientes")
        appendLine()

        // Alarmas y temporizador
        appendLine("⏰ ALARMAS Y TEMPORIZADOR")
        appendLine("• Pon la alarma a las [hora] — Establece alarma")
        appendLine("• Cancela la alarma — Elimina alarmas")
        appendLine("• Temporizador [minutos] — Cuenta regresiva")
        appendLine()

        // Notas
        appendLine("📝 NOTAS")
        appendLine("• Crea una nota de [texto] — Guarda una nota")
        appendLine("• Lee mis notas — Resumen de notas")
        appendLine("• Lee la nota de [tema] — Busca una nota")
        appendLine()

        // Apps y configuración
        appendLine("📱 APLICACIONES")
        appendLine("• Abre [app] — Abre cualquier app")
        appendLine("• Abre ajustes — Configuración del sistema")
        appendLine("• Abre archivo [nombre] — Abre un archivo")
        appendLine()

        // Búsqueda y navegación
        appendLine("🔍 BÚSQUEDA Y NAVEGACIÓN")
        appendLine("• Busca [texto] — Busca en Google")
        appendLine("• Llévame a [lugar] — Navega con GPS")
        appendLine()

        // Volumen y brillo
        appendLine("🔊 VOLUMEN Y BRILLO")
        appendLine("• Sube/baja el volumen — Controla volumen")
        appendLine("• Silencia — Silencio total")
        appendLine("• Sube/baja el brillo — Controla brillo")
        appendLine()

        // Conectividad
        appendLine("📶 CONECTIVIDAD")
        appendLine("• Enciende/apaga WiFi — Control WiFi")
        appendLine("• Enciende/apaga Bluetooth — Control BT")
        appendLine("• Activa/desactiva datos móviles — Datos")
        appendLine("• Activa modo avión — Modo avión")
        appendLine()

        // Dispositivo
        appendLine("🔋 DISPOSITIVO")
        appendLine("• Cuánta bateria queda — Nivel de batería")
        appendLine("• Qué modelo es mi telefono — Info del device")
        appendLine("• Cuánto espacio libre — Almacenamiento")
        appendLine("• Qué wifi tengo — Info de red")
        appendLine("• Dónde estoy — Ubicación GPS")
        appendLine()

        // Utilidades
        appendLine("🧰 UTILIDADES")
        appendLine("• Calcula [operación] — Calculadora")
        appendLine("• Copia [texto] — Copia al portapapeles")
        appendLine("• Pega — Pega desde portapapeles")
        appendLine("• Crea una nota de [texto] — Guarda nota")
        appendLine("• Recuerda que [dato] — Guarda en memoria")
        appendLine()

        // Cámara y multimedia
        appendLine("📷 CÁMARA Y MULTIMEDIA")
        appendLine("• Saca una foto — Foto con cámara trasera")
        appendLine("• Selfie — Foto frontal")
        appendLine("• Escanea código QR — Lee QR")
        appendLine("• Lee el texto de la pantalla — OCR")
        appendLine()

        // Traducción
        appendLine("🌐 TRADUCCIÓN")
        appendLine("• Traduce [texto] al [idioma] — Traduce offline")
        appendLine("  Idiomas: inglés, francés, alemán, portugués, chino, japonés")
        appendLine()

        // Monitoreo y análisis
        appendLine("👁️ MONITOREO")
        appendLine("• Analiza mi pantalla — Describe lo que ve")
        appendLine("• Activa monitoreo — Monitoreo continuo")
        appendLine("• Detén monitoreo — Para el monitoreo")
        appendLine()

        // Secuencias
        appendLine("🔗 SECUENCIAS")
        appendLine("• [comando] y luego [comando] — Ejecuta varios pasos")
        appendLine("• Primero [A] y después [B] — Orden específico")
        appendLine()

        // Otros
        appendLine("⚙️ OTROS")
        appendLine("• Vibra [duración] — Vibración")
        appendLine("• Cambia a inglés/español — Idioma del asistente")
        appendLine("• Repite — Repite la última respuesta")
        appendLine("• Entra en modo|[nombre] — Cambia atuendo")
        appendLine()

        appendLine("💡 Puedes encadenar comandos:")
        appendLine("  \"Primero pon la alarma a las 7 y luego busca recetas\"")
        appendLine()
        appendLine("También puedo aprender tu nombre. ¡Dime \"Me llamo [tu nombre]\"!")
    }
}
