package com.screenassistant.feature.overlay

import com.screenassistant.feature.overlay.R
import org.junit.Assert.*
import org.junit.Test

class CharacterStateTest {

    @Test
    fun `initial state is IDLE`() {
        val state = CharacterState()
        assertEquals(AnimationState.IDLE, state.animationState)
    }

    @Test
    fun `nextOutfit cycles through outfits and updates currentAssetRes`() {
        val state = CharacterState()
        val firstRes = state.currentAssetRes
        assertTrue(firstRes != 0)

        state.nextOutfit()
        val secondRes = state.currentAssetRes
        assertTrue(secondRes != 0)

        assertNotEquals(firstRes, secondRes)
    }

    @Test
    fun `nextOutfit wraps around after full cycle`() {
        val state = CharacterState()
        // Avanzar un outfit
        state.nextOutfit()
        val firstRes = state.currentAssetRes
        // La biblioteca tiene 10 atuendos.
        // 10 llamadas más completan el ciclo completo (vuelta al outfit inicial)
        repeat(10) { state.nextOutfit() }
        assertEquals(firstRes, state.currentAssetRes)
    }

    @Test
    fun `assistantText starts empty`() {
        val state = CharacterState()
        assertTrue(state.assistantText.isEmpty())
    }

    @Test
    fun `currentOutfitRes overrides state-based asset`() {
        val state = CharacterState()
        val customRes = R.drawable.ic_assistant_placeholder
        state.currentOutfitRes = customRes
        assertEquals(customRes, state.currentAssetRes)
    }

    @Test
    fun `currentAssetRes returns a valid resource`() {
        val state = CharacterState()
        assertTrue(state.currentAssetRes != 0)
    }

    @Test
    fun `changing animationState returns state-specific asset resource when available`() {
        val state = CharacterState()
        state.animationState = AnimationState.IDLE
        val idleRes = state.currentAssetRes
        assertTrue(idleRes != 0)

        // THINKING tiene un asset específico en la biblioteca
        state.animationState = AnimationState.THINKING
        val thinkingRes = state.currentAssetRes
        assertTrue(thinkingRes != 0)
        assertNotEquals(idleRes, thinkingRes)
    }

    @Test
    fun `RESPONDING state falls back to IDLE if not defined in outfit`() {
        val state = CharacterState()
        // RESPONDING está definido en Base pero no en todos.
        // En la implementación actual, busca en Base si no está en el actual.
        state.animationState = AnimationState.RESPONDING
        val res = state.currentAssetRes
        assertTrue(res != 0)
    }
}
