package com.screenassistant.feature.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.screenassistant.feature.overlay.R

enum class AnimationState {
    IDLE,           // Posición base / Espera
    THINKING,       // Pensando (mano en barbilla)
    RESPONDING,     // Respondiendo con alegría
    GREETING,       // Bienvenida (saludando con las manos)
    LISTENING,      // Escuchando atentamente
    SPEAKING,       // Hablando (sincronizado con voz)
    ANALYZING,      // Inspección / Rodillas con datos (viendo tablet o pantalla)
    MEDITATING,     // Relajación / Meditación
    TUTORING,       // Tutoría / Explicación (con tablet flotante)
    SQUATTING       // Agachada completa / Cucclillas
}

data class Outfit(
    val name: String,
    val poses: Map<AnimationState, Int>
)

class CharacterState {

    // BIBLIOTECA DE 9 ATUENDOS (Uno por cada pose base disponible)
    private val library = listOf(
        Outfit(
            name = "Técnico (Base)",
            poses = mapOf(
                AnimationState.IDLE to R.drawable.char_idle,
                AnimationState.THINKING to R.drawable.char_thinking,
                AnimationState.ANALYZING to R.drawable.char_kneeling,
                AnimationState.GREETING to R.drawable.char_greeting,
                AnimationState.RESPONDING to R.drawable.char_responding,
                AnimationState.TUTORING to R.drawable.char_tutoring,
                AnimationState.SQUATTING to R.drawable.char_squatting,
                AnimationState.MEDITATING to R.drawable.char_meditating
            )
        ),
        Outfit(
            name = "Analista",
            poses = mapOf(
                AnimationState.IDLE to R.drawable.char_inspecting,
                AnimationState.ANALYZING to R.drawable.char_inspecting,
                AnimationState.THINKING to R.drawable.char_thinking,
                AnimationState.RESPONDING to R.drawable.char_responding,
                AnimationState.TUTORING to R.drawable.char_tutoring
            )
        ),
        Outfit(
            name = "Pensativa",
            poses = mapOf(
                AnimationState.IDLE to R.drawable.char_thinking,
                AnimationState.THINKING to R.drawable.char_thinking,
                AnimationState.RESPONDING to R.drawable.char_responding,
                AnimationState.ANALYZING to R.drawable.char_kneeling
            )
        ),
        Outfit(
            name = "Anfitriona",
            poses = mapOf(
                AnimationState.IDLE to R.drawable.char_greeting,
                AnimationState.GREETING to R.drawable.char_greeting,
                AnimationState.RESPONDING to R.drawable.char_responding,
                AnimationState.TUTORING to R.drawable.char_tutoring
            )
        ),
        Outfit(
            name = "Investigadora",
            poses = mapOf(
                AnimationState.IDLE to R.drawable.char_kneeling,
                AnimationState.ANALYZING to R.drawable.char_kneeling,
                AnimationState.THINKING to R.drawable.char_thinking,
                AnimationState.SQUATTING to R.drawable.char_squatting
            )
        ),
        Outfit(
            name = "Alegre",
            poses = mapOf(
                AnimationState.IDLE to R.drawable.char_responding,
                AnimationState.RESPONDING to R.drawable.char_responding,
                AnimationState.GREETING to R.drawable.char_greeting,
                AnimationState.TUTORING to R.drawable.char_tutoring
            )
        ),
        Outfit(
            name = "Tutora",
            poses = mapOf(
                AnimationState.IDLE to R.drawable.char_tutoring,
                AnimationState.TUTORING to R.drawable.char_tutoring,
                AnimationState.RESPONDING to R.drawable.char_responding,
                AnimationState.THINKING to R.drawable.char_thinking
            )
        ),
        Outfit(
            name = "Atlética",
            poses = mapOf(
                AnimationState.IDLE to R.drawable.char_squatting,
                AnimationState.SQUATTING to R.drawable.char_squatting,
                AnimationState.ANALYZING to R.drawable.char_inspecting,
                AnimationState.MEDITATING to R.drawable.char_meditating
            )
        ),
        Outfit(
            name = "Zen",
            poses = mapOf(
                AnimationState.IDLE to R.drawable.char_meditating,
                AnimationState.MEDITATING to R.drawable.char_meditating,
                AnimationState.SQUATTING to R.drawable.char_squatting,
                AnimationState.THINKING to R.drawable.char_thinking
            )
        ),
        Outfit(
            name = "Analista de Datos",
            poses = mapOf(
                AnimationState.IDLE to R.drawable.char_data_analyst,
                AnimationState.ANALYZING to R.drawable.char_data_analyst,
                AnimationState.TUTORING to R.drawable.char_data_analyst,
                AnimationState.THINKING to R.drawable.char_thinking,
                AnimationState.RESPONDING to R.drawable.char_responding
            )
        )
    )

    var currentOutfitIndex by mutableIntStateOf(0)
        private set

    var currentOutfitRes by mutableStateOf<Int?>(null)
    var animationState by mutableStateOf(AnimationState.IDLE)
    var assistantText by mutableStateOf("")

    /** M16 (Lote 8): número de atuendos de la biblioteca — el ViewModel lo usa
     *  para el módulo del índice del UI (fuente única: library.size). */
    val outfitCount: Int get() = library.size

    // Resolución de imagen con prioridad en el cambio de ropa
    val currentAssetRes: Int
        get() {
            currentOutfitRes?.let { return it }

            val currentOutfit = library[currentOutfitIndex]

            // Prioridad:
            // 1. Pose específica en el atuendo actual
            // 2. Pose IDLE en el atuendo actual (para asegurar que se vea el cambio de ropa)
            // 3. Pose IDLE del atuendo base (como último recurso seguro)
            return currentOutfit.poses[animationState]
                ?: currentOutfit.poses[AnimationState.IDLE]
                ?: library[0].poses[AnimationState.IDLE]
                ?: R.drawable.ic_assistant_placeholder
        }

    fun nextOutfit() {
        currentOutfitIndex = (currentOutfitIndex + 1) % library.size
        // Resetear la animación a IDLE al cambiar de ropa para feedback inmediato
        animationState = AnimationState.IDLE
    }

    fun setOutfit(index: Int) {
        if (index in library.indices) {
            currentOutfitIndex = index
            // Opcional: resetear animación o mantener la actual si aplica
        }
    }

    fun updateAnimation(state: AnimationState) {
        animationState = state
    }
}
