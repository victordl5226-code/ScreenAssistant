package com.screenassistant.core.ai.memory

import com.screenassistant.core.domain.repository.ai.FactCategory
import com.screenassistant.core.domain.repository.ai.FactExtractor
import com.screenassistant.core.domain.repository.ai.KnowledgeFact
import com.screenassistant.core.domain.repository.ai.MemoryEntry
import com.screenassistant.core.domain.repository.ai.MemoryRole
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [FactExtractor] basada en reglas (sin LLM).
 *
 * Analiza mensajes del usuario con patrones regex para extraer hechos
 * y preferencias. Asigna confianza según la fuerza del patrón.
 *
 * ## Categorías soportadas
 * - PREFERENCE: "me gusta X", "mi X favorito es Y", "prefiero X"
 * - CONTACT: "mi nombre es X", "me llamo X"
 * - ROUTINE: "siempre hago X", "cada día X", "mi rutina es X"
 * - WORK: "trabajo en X", "mi proyecto es X"
 * - RELATIONSHIP: "mi madre es X", "mi amigo X se llama Y"
 * - LOCATION: "vivo en X", "mi casa está en X"
 *
 * ## Limitaciones
 * - Solo detecta patrones en español
 * - No maneja contexto ni co-referencia
 * - No distingue entre hechos nuevos y actualizaciones de hechos existentes
 */
@Singleton
class FactExtractorImpl @Inject constructor() : FactExtractor {

    companion object {
        // Patrones de extracción organizados por categoría
        private val PREFERENCE_PATTERNS = listOf(
            // "no me gusta X" — PRIMERO para evitar falso positivo con "me gusta"
            Pattern("no me (?:gusta|encanta|fascina) (?:el |la |las |los )?(.+)", "no me gusta", 0.85f),
            // "me gusta el/la/las/los X" — SOLO si NO precedido por "no"
            Pattern("(?<!no )me (?:gusta|encanta|fascina) (?:el |la |las |los )?(.+)", "me gusta", 0.9f),
            // "mi X favorito/a es Y"
            Pattern("mi (.+?) favorit[oa] es (.+)", "favorito", 0.95f),
            // "prefiero X sobre Y"
            Pattern("prefiero (.+?)(?: sobre| a| antes que) (.+)", "prefiero", 0.85f),
            // "X es mi favorito/a"
            Pattern("(.+?) es mi favorit[oa]", "es favorito", 0.9f),
        )

        private val CONTACT_PATTERNS = listOf(
            // "mi nombre es X" / "me llamo X"
            Pattern("mi nombre es (.+)", "nombre", 0.95f),
            Pattern("me llamo (.+)", "nombre", 0.95f),
            // "mi teléfono es X" / "mi celular es X"
            Pattern("mi (?:teléfono|celular|móvil) es (.+)", "teléfono", 0.9f),
            // "mi correo es X" / "mi email es X"
            Pattern("mi (?:correo|email) es (.+)", "correo", 0.9f),
            // "tengo X años"
            Pattern("tengo (\\d+) años", "edad", 0.95f),
        )

        private val ROUTINE_PATTERNS = listOf(
            // "siempre hago X" / "siempre me levanto a las X"
            Pattern("siempre (.+)", "rutina", 0.8f),
            // "cada día X" / "cada mañana X"
            Pattern("cada (?:día|mañana|tarde|noche|semana) (.+)", "rutina diaria", 0.85f),
            // "mi rutina es X"
            Pattern("mi rutina es (.+)", "rutina", 0.9f),
            // "me levanto a las X"
            Pattern("me levanto a las (.+)", "hora levantarse", 0.85f),
            // "me acuesto a las X"
            Pattern("me acuesto a las (.+)", "hora dormir", 0.85f),
        )

        private val WORK_PATTERNS = listOf(
            // "trabajo en X"
            Pattern("trabajo en (.+)", "lugar trabajo", 0.9f),
            // "mi trabajo es X"
            Pattern("mi trabajo es (.+)", "trabajo", 0.9f),
            // "soy X" (profesión)
            Pattern("soy (.+?)(?: de| en| para|$)", "profesión", 0.8f),
            // "mi proyecto es X"
            Pattern("mi proyecto (?:es|actual)\\s+(.+)", "proyecto", 0.85f),
            // "tengo una reunión con X"
            Pattern("tengo una reunión con (.+)", "reunión", 0.8f),
        )

        private val RELATIONSHIP_PATTERNS = listOf(
            // "mi madre/padre/hermano/amigo se llama X"
            Pattern("mi (?:madre|padre|herman[oa]|amig[oa]|espos[oa]|pareja|novi[oa]) se llama (.+)", "contacto", 0.9f),
            // "mi madre/padre es X"
            Pattern("mi (?:madre|padre|herman[oa]|amig[oa]|espos[oa]|pareja|novi[oa]) es (.+)", "relación", 0.85f),
            // "tengo un/a hijo/a que se llama X"
            Pattern("tengo (?:un |una )?(?:hij[oa]) que se llama (.+)", "hijo/a", 0.9f),
        )

        private val LOCATION_PATTERNS = listOf(
            // "vivo en X"
            Pattern("vivo en (.+)", "ubicación", 0.9f),
            // "mi casa está en X"
            Pattern("mi casa (?:está|queda|está en) (.+)", "casa", 0.9f),
            // "mi oficina está en X"
            Pattern("mi oficina (?:está|queda|está en) (.+)", "oficina", 0.85f),
            // "soy de X"
            Pattern("soy de (.+?)(?:\\s*,|$)", "origen", 0.8f),
        )

        private val ALL_PATTERNS = listOf(
            FactCategory.PREFERENCE to PREFERENCE_PATTERNS,
            FactCategory.CONTACT to CONTACT_PATTERNS,
            FactCategory.ROUTINE to ROUTINE_PATTERNS,
            FactCategory.WORK to WORK_PATTERNS,
            FactCategory.RELATIONSHIP to RELATIONSHIP_PATTERNS,
            FactCategory.LOCATION to LOCATION_PATTERNS,
        )
    }

    /**
     * Patrón de extracción con regex, descripción y confianza base.
     */
    private data class Pattern(
        val regex: String,
        val description: String,
        val baseConfidence: Float,
    ) {
        val compiledRegex: Regex = Regex(regex, RegexOption.IGNORE_CASE)
    }

    override suspend fun extractFacts(entries: List<MemoryEntry>): List<KnowledgeFact> {
        val facts = mutableListOf<KnowledgeFact>()

        for (entry in entries) {
            // Solo analizar mensajes del usuario
            if (entry.role != MemoryRole.USER) continue

            val extractedFacts = extractFromMessage(entry.content)
            facts.addAll(extractedFacts)
        }

        return facts.distinctBy { "${it.category}:${it.subject}:${it.predicate}" }
    }

    override suspend fun extractFromMessage(message: String): List<KnowledgeFact> {
        val facts = mutableListOf<KnowledgeFact>()
        val normalizedMessage = message.lowercase().trim()

        for ((category, patterns) in ALL_PATTERNS) {
            for (pattern in patterns) {
                val match = pattern.compiledRegex.find(normalizedMessage)
                if (match != null) {
                    // Extraer del mensaje ORIGINAL (preservar mayúsculas)
                    val originalMatch = pattern.compiledRegex.find(message)
                    val fact = createFact(category, pattern, match, originalMatch)
                    if (fact != null) {
                        facts.add(fact)
                    }
                }
            }
        }

        return facts
    }

    /**
     * Crea un [KnowledgeFact] a partir de un match de patrón.
     *
     * @return KnowledgeFact o null si el contenido extraído no es significativo
     */
    private fun createFact(
        category: FactCategory,
        pattern: Pattern,
        match: MatchResult,
        originalMatch: MatchResult?,
    ): KnowledgeFact? {
        val subject = when (category) {
            FactCategory.PREFERENCE -> "preferencia"
            FactCategory.CONTACT -> pattern.description
            FactCategory.ROUTINE -> pattern.description
            FactCategory.WORK -> pattern.description
            FactCategory.RELATIONSHIP -> pattern.description
            FactCategory.LOCATION -> pattern.description
            FactCategory.OTHER -> "general"
        }

        val predicate = pattern.description
        val value = extractValue(originalMatch ?: match)

        // No crear hechos con valores vacíos o muy cortos
        if (value.isBlank() || value.length < 2) return null

        return KnowledgeFact(
            category = category,
            subject = subject,
            predicate = predicate,
            `object` = value,
            confidence = pattern.baseConfidence,
            source = "rule_based",
        )
    }

    /**
     * Extrae el valor significativo del match.
     * Prioriza grupos de captura, si no usa el match completo.
     */
    private fun extractValue(match: MatchResult): String {
        // Si hay grupos de captura, usar el último grupo (más específico)
        return if (match.groupValues.size > 1) {
            match.groupValues.last().trim()
        } else {
            match.value.trim()
        }
    }
}
