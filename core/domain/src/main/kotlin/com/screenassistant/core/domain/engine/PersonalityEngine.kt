package com.screenassistant.core.domain.engine

import com.screenassistant.core.domain.model.personality.PersonalityConfig
import com.screenassistant.core.domain.model.personality.PersonalityTraits
import javax.inject.Inject

/**
 * Motor de personalidad: adapta el tono de las respuestas
 * según los rasgos configurados en [PersonalityTraits].
 *
 * 100% puro — sin dependencias Android. Toda la lógica
 * de transformación de texto es determinista y testeable.
 *
 * @constructor Crea el motor sin dependencias externas.
 */
class PersonalityEngine @Inject constructor() {

    /**
     * Adapta una respuesta al tono de personalidad configurado.
     *
     * Aplica, en orden: formalidad, calidez y humor.
     * Cada transformación es idempotente (aplicarla dos veces
     * no cambia el resultado).
     *
     * @param response Texto original de la respuesta
     * @param traits Rasgos de personalidad que definen el tono
     * @return Texto adaptado al tono configurado
     */
    fun adaptResponse(response: String, traits: PersonalityTraits): String {
        var adapted = response
        adapted = adjustFormality(adapted, traits.formality)
        if (traits.warmth > WARMTH_THRESHOLD) {
            adapted = addWarmth(adapted)
        }
        if (traits.humor > HUMOR_MIN_THRESHOLD && shouldAddHumor(traits)) {
            adapted = addHumorTouch(adapted, traits.humor)
        }
        return adapted
    }

    /**
     * Determina si se debe añadir un toque de humor basándose en el rasgo.
     *
     * Usa una función hash determinista basada en los rasgos para
     * generar una probabilidad consistente — no usa [kotlin.random.Random]
     * para mantener la determinismática del motor.
     *
     * @param traits Rasgos de personalidad
     * @return true si se debe añadir humor en esta invocación
     */
    fun shouldAddHumor(traits: PersonalityTraits): Boolean {
        val probability = traits.humor * HUMOR_PROBABILITY_FACTOR
        val hash = (traits.formality * FORMALITY_HASH_FACTOR + traits.humor * HUMOR_HASH_FACTOR).toInt()
        return (hash % PERCENT_BASE) < (probability * PERCENT_BASE).toInt()
    }

    /**
     * Construye un saludo con personalidad según la hora del día.
     *
     * @param config Configuración completa de personalidad (nombre + rasgos)
     * @param timeOfDay Período del día: "morning", "afternoon", "evening" o null
     * @return Saludo adaptado al tono y momento del día
     */
    fun buildGreeting(config: PersonalityConfig, timeOfDay: String?): String {
        val base = when (timeOfDay) {
            "morning" -> "Buenos días"
            "afternoon" -> "Buenas tardes"
            "evening" -> "Buenas noches"
            else -> "Hola"
        }
        val name = config.name
        return when {
            config.traits.formality > FORMALITY_HIGH_THRESHOLD ->
                "$base. $name a su servicio."
            config.traits.warmth > WARMTH_THRESHOLD ->
                "¡$base! ¿Cómo estás?"
            config.traits.humor > HUMOR_MIN_THRESHOLD ->
                "$base. ¿En qué puedo ayudarle hoy, o prefiere que adivine?"
            else ->
                "$base. ¿Cómo puedo ayudarle?"
        }
    }

    /**
     * Ajusta la formalidad del texto reemplazando expresiones informales.
     */
    private fun adjustFormality(text: String, formality: Float): String {
        if (formality > FORMALITY_HIGH_THRESHOLD) {
            return text
                .replace(Regex("\\bhey\\b", RegexOption.IGNORE_CASE), "estimado")
                .replace(Regex("\\bcheers\\b", RegexOption.IGNORE_CASE), "atentamente")
        }
        return text
    }

    /**
     * Añade un prefijo de calidez al texto si no empieza con uno ya.
     */
    private fun addWarmth(text: String): String {
        if (WARM_PREFIXES.any { text.startsWith(it, ignoreCase = true) }) return text
        return "¡$text"
    }

    /**
     * Añade un sufijo lúdico al texto según el nivel de humor.
     */
    private fun addHumorTouch(text: String, humorLevel: Float): String {
        if (humorLevel < HUMOR_MIN_THRESHOLD) return text
        val index = text.length % HUMOR_SUFFIXES.size
        return text + HUMOR_SUFFIXES[index]
    }

    private companion object {
        /** Umbral de calidez para activar transformaciones cálidas. */
        const val WARMTH_THRESHOLD = 0.7f

        /** Umbral mínimo de humor para considerar añadir un toque. */
        const val HUMOR_MIN_THRESHOLD = 0.5f

        /** Umbral de formalidad para activar reemplazos formales. */
        const val FORMALITY_HIGH_THRESHOLD = 0.7f

        /** Factor multiplicador para calcular la probabilidad de humor. */
        const val HUMOR_PROBABILITY_FACTOR = 0.6f

        /** Factor de peso de formalidad en el hash determinista. */
        const val FORMALITY_HASH_FACTOR = 1000f

        /** Factor de peso de humor en el hash determinista. */
        const val HUMOR_HASH_FACTOR = 100f

        /** Base para cálculo porcentual. */
        const val PERCENT_BASE = 100

        /** Prefijos que indican que el texto ya tiene calidez. */
        val WARM_PREFIXES = listOf("¡", "Claro", "Por supuesto", "Con gusto")

        /** Sufijos humorísticos para añadir al final de la respuesta. */
        val HUMOR_SUFFIXES = listOf(" 😊", " —como siempre.", ". ¿Algo más?", ". Estoy a su servicio.")
    }
}
