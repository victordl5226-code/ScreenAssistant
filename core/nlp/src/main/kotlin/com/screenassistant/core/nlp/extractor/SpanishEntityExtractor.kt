package com.screenassistant.core.nlp.extractor

import com.screenassistant.core.domain.model.AssistantLanguage
import com.screenassistant.core.domain.model.CalculatorOperator
import com.screenassistant.core.domain.model.TranslateLanguage
import com.screenassistant.core.domain.model.VolumeAction
import com.screenassistant.core.domain.nlp.EntityExtractor
import com.screenassistant.core.domain.nlp.model.NlpEntity

/**
 * Extractor de entidades para el idioma español.
 *
 * Usa regex especializadas para extraer: horas, contactos, teléfonos,
 * aplicaciones, destinos, textos, números, duraciones, volumen, on/off,
 * idiomas, expresiones matemáticas, vibración y opciones de foto.
 */
class SpanishEntityExtractor : EntityExtractor {

    override fun extraer(textoNormalizado: String, textoOriginal: String): List<NlpEntity> {
        if (textoNormalizado.isBlank()) return emptyList()

        val entidades = mutableListOf<NlpEntity>()

        entidades.addAll(extraerHoras(textoNormalizado, textoOriginal))
        entidades.addAll(extraerNumeros(textoNormalizado, textoOriginal))
        entidades.addAll(extraerDuracion(textoNormalizado, textoOriginal))
        entidades.addAll(extraerTelefono(textoNormalizado, textoOriginal))
        entidades.addAll(extraerContacto(textoNormalizado, textoOriginal))
        entidades.addAll(extraerVolumen(textoNormalizado, textoOriginal))
        entidades.addAll(extraerOnOff(textoNormalizado, textoOriginal))
        entidades.addAll(extraerAplicacion(textoNormalizado, textoOriginal))
        entidades.addAll(extraerDestino(textoNormalizado, textoOriginal))
        entidades.addAll(extraerCalculadora(textoNormalizado, textoOriginal))
        entidades.addAll(extraerBrillo(textoNormalizado, textoOriginal))
        entidades.addAll(extraerVibracion(textoNormalizado, textoOriginal))
        entidades.addAll(extraerFoto(textoNormalizado, textoOriginal))
        entidades.addAll(extraerIdioma(textoNormalizado, textoOriginal))
        entidades.addAll(extraerTexto(textoNormalizado, textoOriginal))

        return entidades
    }

    // ── Horas ──────────────────────────────────────────────────

    private fun extraerHoras(normalizado: String, original: String): List<NlpEntity> {
        val patrones = listOf(
            // "a las 7:30", "para las 14", "al 8"
            Regex("""(?:a\s+las?|para\s+las?|al)\s+(\d{1,2})[:\s]?(?:y\s+)?(\d{2})?\s*(?:de\s+(?:la\s+)?(manana|tarde|noche))?"""),
            // "a las 7 y media", "a las 7 y cuarto"
            Regex("""(?:a\s+las?|para\s+las?|al)\s+(\d{1,2})\s+y\s+(?:la\s+)?(cuarto|media)"""),
            // "a las 7 en punto"
            Regex("""(?:a\s+las?|para\s+las?|al)\s+(\d{1,2})\s+en\s+punto""")
        )

        for (patron in patrones) {
            val match = patron.find(normalizado) ?: continue
            val hora = match.groupValues[1].toIntOrNull() ?: continue
            if (hora !in 0..23) continue

            val minuto = when {
                match.groupValues.size > 2 && match.groupValues[2] == "30" -> 30
                match.groupValues.size > 2 && match.groupValues[2] == "15" -> 15
                match.groupValues.size > 2 && match.groupValues[2] == "45" -> 45
                match.value.contains("media") -> 30
                match.value.contains("cuarto") -> 15
                else -> 0
            }

            // Detectar AM/PM implícito
            val textoRestante = if (match.range.last + 1 < normalizado.length) {
                normalizado.substring(match.range.last + 1).take(20)
            } else ""
            val horaAjustada = when {
                textoRestante.contains("de la tarde") && hora < 12 -> hora + 12
                textoRestante.contains("de la noche") && hora < 12 -> hora + 12
                else -> hora
            }

            return listOf(NlpEntity.Hora(horaAjustada, minuto, match.value))
        }
        return emptyList()
    }

    // ── Números ────────────────────────────────────────────────

    private fun extraerNumeros(normalizado: String, original: String): List<NlpEntity> {
        val patron = Regex("""(\d+)""")
        val match = patron.find(normalizado) ?: return emptyList()
        val valor = match.groupValues[1].toIntOrNull() ?: return emptyList()
        return listOf(NlpEntity.Numero(valor, match.value))
    }

    // ── Duración ───────────────────────────────────────────────

    private fun extraerDuracion(normalizado: String, original: String): List<NlpEntity> {
        val patrones = listOf(
            // "5 minutos", "10 horas", "30 segundos"
            Regex("""(\d+)\s*(?:min(?:uto)?s?|horas?|seg(?:undo)?s?)"""),
            // "un minuto", "una hora"
            Regex("""(?:un|una)\s+(minuto|hora|segundo)"""),
            // "dos horas y media"
            Regex("""(\d+)\s+horas?\s+y\s+media"""),
            // "hora y media"
            Regex("""(?:una?\s+)?hora\s+y\s+media""")
        )

        for (patron in patrones) {
            val match = patron.find(normalizado) ?: continue
            val minutos = when {
                match.value.contains("hora") && match.value.contains("media") -> 90
                match.value.contains("hora") -> {
                    val num = match.groupValues[1].toIntOrNull()
                    if (num != null) num * 60 else 60
                }
                match.value.contains("minuto") -> {
                    match.groupValues[1].toIntOrNull() ?: 1
                }
                match.value.contains("segundo") -> {
                    val num = match.groupValues[1].toIntOrNull() ?: 1
                    (num / 60.0).coerceAtLeast(1.0).toInt()
                }
                else -> continue
            }
            return listOf(NlpEntity.Duracion(minutos, match.value))
        }
        return emptyList()
    }

    // ── Teléfono ───────────────────────────────────────────────

    private fun extraerTelefono(normalizado: String, original: String): List<NlpEntity> {
        val patron = Regex("""(?:al?|numero|tel(?:efono|fono)?)\s+(\+?\d[\d\s\-.]{6,})""")
        val match = patron.find(normalizado) ?: return emptyList()
        val numero = match.groupValues[1].replace(Regex("""[\s\-.]"""), "")
        return listOf(NlpEntity.Telefono(numero, match.value))
    }

    // ── Contacto ───────────────────────────────────────────────

    private fun extraerContacto(normalizado: String, original: String): List<NlpEntity> {
        val patron = Regex("""(?:llama|llamar|contacta|manda|envia|mensaje)\s+(?:al?|a)\s+([a-zA-Záéíóúñü]+(?:\s+[a-zA-Záéíóúñü]+)*)""")
        val match = patron.find(normalizado) ?: return emptyList()
        val nombre = match.groupValues[1].trim().lowercase()
        if (nombre.length < 2) return emptyList()
        return listOf(NlpEntity.Contacto(nombre, match.value))
    }

    // ── Volumen ────────────────────────────────────────────────

    private fun extraerVolumen(normalizado: String, original: String): List<NlpEntity> {
        return when {
            normalizado.contains(Regex("""sube(?:r)?\s+(?:el\s+)?volumen""")) ->
                listOf(NlpEntity.AccionVolumen(VolumeAction.UP, "subir volumen"))
            normalizado.contains(Regex("""baja(?:r)?\s+(?:el\s+)?volumen""")) ->
                listOf(NlpEntity.AccionVolumen(VolumeAction.DOWN, "bajar volumen"))
            normalizado.contains(Regex("""(?:silencio|silencia|mutear)""")) ->
                listOf(NlpEntity.AccionVolumen(VolumeAction.MUTE, "silenciar"))
            normalizado.contains(Regex("""volumen\s+(?:al\s+)?maximo""")) ->
                listOf(NlpEntity.AccionVolumen(VolumeAction.MAX, "máximo"))
            normalizado.contains(Regex("""volumen\s+(?:al\s+)?minimo""")) ->
                listOf(NlpEntity.AccionVolumen(VolumeAction.MIN, "mínimo"))
            else -> emptyList()
        }
    }

    // ── On/Off ─────────────────────────────────────────────────

    private fun extraerOnOff(normalizado: String, original: String): List<NlpEntity> {
        val activar = Regex("""(?:activa|enciende|encender|prende|prender|abre|abrir)""")
        val desactivar = Regex("""(?:desactiva|apaga|apagar|cierra|cerrar|quita|quitar)""")

        return when {
            activar.containsMatchIn(normalizado) -> listOf(NlpEntity.OnOff(true, original))
            desactivar.containsMatchIn(normalizado) -> listOf(NlpEntity.OnOff(false, original))
            else -> emptyList()
        }
    }

    // ── Aplicación ─────────────────────────────────────────────

    private fun extraerAplicacion(normalizado: String, original: String): List<NlpEntity> {
        val patron = Regex("""^(?:abre|abrir)\s+(.+)$""")
        val match = patron.find(normalizado) ?: return emptyList()
        val app = match.groupValues[1].trim()
        if (app.length < 2) return emptyList()
        return listOf(NlpEntity.Aplicacion(app, match.value))
    }

    // ── Destino ────────────────────────────────────────────────

    private fun extraerDestino(normalizado: String, original: String): List<NlpEntity> {
        val patron = Regex("""(?:llevame|llévame|navega|ir)\s+a\s+(.+)$""")
        val match = patron.find(normalizado) ?: return emptyList()
        val destino = match.groupValues[1].trim()
        if (destino.length < 2) return emptyList()
        return listOf(NlpEntity.Destino(destino, match.value))
    }

    // ── Calculadora ────────────────────────────────────────────

    private fun extraerCalculadora(normalizado: String, original: String): List<NlpEntity> {
        val patron = Regex("""(\d+(?:\.\d+)?)\s*(?:\+|\-|\*|\/|por|entre|mas|menos)\s*(\d+(?:\.\d+)?)""")
        val match = patron.find(normalizado) ?: return emptyList()
        val op1 = match.groupValues[1].toDoubleOrNull() ?: return emptyList()
        val op2 = match.groupValues[2].toDoubleOrNull() ?: return emptyList()
        val operador = when {
            match.value.contains(Regex("""\+|mas""")) -> CalculatorOperator.ADD
            match.value.contains(Regex("""\-|menos""")) -> CalculatorOperator.SUBTRACT
            match.value.contains(Regex("""\*|por""")) -> CalculatorOperator.MULTIPLY
            match.value.contains(Regex("""\/|entre""")) -> CalculatorOperator.DIVIDE
            else -> CalculatorOperator.ADD
        }
        return listOf(NlpEntity.ExpresionCalculadora(op1, operador, op2, match.value))
    }

    // ── Brillo ─────────────────────────────────────────────────

    private fun extraerBrillo(normalizado: String, original: String): List<NlpEntity> {
        val patron = Regex("""(?:brillo|luminosidad)\s+(?:a|al|en)\s+(\d+)""")
        val match = patron.find(normalizado) ?: return emptyList()
        val valor = match.groupValues[1].toIntOrNull() ?: return emptyList()
        return listOf(NlpEntity.Numero(valor, match.value))
    }

    // ── Vibración ──────────────────────────────────────────────

    private fun extraerVibracion(normalizado: String, original: String): List<NlpEntity> {
        if (!normalizado.contains(Regex("""vib(?:ra|rar)"""))) return emptyList()
        val patron = Regex("""vib(?:ra|rar)\s+(?:por\s+)?(\d+)\s*(?:segundo|s)""")
        val match = patron.find(normalizado)
        val duracionMs = if (match != null) {
            (match.groupValues[1].toLongOrNull() ?: 1L) * 1000
        } else {
            500L // Vibración por defecto: 500ms
        }
        return listOf(NlpEntity.Vibracion(duracionMs, original))
    }

    // ── Foto ───────────────────────────────────────────────────

    private fun extraerFoto(normalizado: String, original: String): List<NlpEntity> {
        val frontal = normalizado.contains(Regex("""selfie|frontal|delantera|camara\s+delantera"""))
        return listOf(NlpEntity.Foto(frontal, original))
    }

    // ── Idioma ─────────────────────────────────────────────────

    private fun extraerIdioma(normalizado: String, original: String): List<NlpEntity> {
        val idioma = when {
            normalizado.contains(Regex("""ingles|english""")) -> AssistantLanguage.ENGLISH
            normalizado.contains(Regex("""frances|français""")) -> AssistantLanguage.SPANISH // Placeholder
            normalizado.contains(Regex("""aleman|deutsch""")) -> AssistantLanguage.SPANISH // Placeholder
            normalizado.contains(Regex("""portugues|português""")) -> AssistantLanguage.SPANISH // Placeholder
            normalizado.contains(Regex("""espanol|español|castellano""")) -> AssistantLanguage.SPANISH
            else -> return emptyList()
        }
        return listOf(NlpEntity.Idioma(idioma, original))
    }

    // ── Texto (búsqueda, nota, etc.) ──────────────────────────

    private fun extraerTexto(normalizado: String, original: String): List<NlpEntity> {
        // Buscar contenido después de marcadores comunes
        val patrones = listOf(
            Regex("""(?:busca|buscar)\s+(.+)$"""),
            Regex("""(?:anota|toma nota|escribe|crea)\s+(?:una\s+nota\s+(?:de\s+)?)?(.+)$"""),
            Regex("""recuerda(?:\s+que)?\s+(.+)$"""),
            Regex("""(?:copia|copiar)\s+(.+)$"""),
            Regex("""(?:traduce|traducir)\s+(.+?)(?:\s+(?:al|a)\s+\w+)?$"""),
            Regex("""(?:lee|leer)\s+la\s+nota\s+(?:de|del|:)\s*(.+)$""")
        )

        for (patron in patrones) {
            val match = patron.find(normalizado) ?: continue
            val contenido = match.groupValues.last().trim()
            if (contenido.length >= 2) {
                return listOf(NlpEntity.Texto(contenido, match.value))
            }
        }
        return emptyList()
    }
}
