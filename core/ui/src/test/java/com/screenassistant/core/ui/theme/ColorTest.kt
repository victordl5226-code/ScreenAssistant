package com.screenassistant.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitarios para los colores del tema ScreenAssistant.
 *
 * Verifica que los colores estén definidos correctamente.
 * Nota: Compose Color.value incluye metadata de color space,
 * por lo que verificamos propiedades indirectly.
 */
class ColorTest {

    // ── Light Theme colors exist and are distinct ────────────────────────

    @Test
    fun `Light Primary is defined`() {
        assertNotNull(Primary)
        assertTrue("Primary should have non-zero value", Primary.value != 0uL)
    }

    @Test
    fun `OnPrimary is defined`() {
        assertNotNull(OnPrimary)
        assertTrue("OnPrimary should have non-zero value", OnPrimary.value != 0uL)
    }

    @Test
    fun `PrimaryContainer is defined`() {
        assertNotNull(PrimaryContainer)
    }

    @Test
    fun `OnPrimaryContainer is defined`() {
        assertNotNull(OnPrimaryContainer)
    }

    @Test
    fun `Secondary is defined`() {
        assertNotNull(Secondary)
    }

    @Test
    fun `Background is defined`() {
        assertNotNull(Background)
    }

    @Test
    fun `Error is defined`() {
        assertNotNull(Error)
    }

    // ── Assistant Colors (Light) ─────────────────────────────────────────

    @Test
    fun `AssistantBubble is defined`() {
        assertNotNull(AssistantBubble)
    }

    @Test
    fun `UserBubble is defined`() {
        assertNotNull(UserBubble)
    }

    @Test
    fun `OnAssistantBubble is defined`() {
        assertNotNull(OnAssistantBubble)
    }

    @Test
    fun `OnUserBubble is defined`() {
        assertNotNull(OnUserBubble)
    }

    // ── Dark Theme colors exist ──────────────────────────────────────────

    @Test
    fun `DarkPrimary is defined`() {
        assertNotNull(DarkPrimary)
        assertTrue("DarkPrimary should have non-zero value", DarkPrimary.value != 0uL)
    }

    @Test
    fun `DarkOnPrimary is defined`() {
        assertNotNull(DarkOnPrimary)
    }

    @Test
    fun `DarkBackground is defined`() {
        assertNotNull(DarkBackground)
    }

    @Test
    fun `DarkError is defined`() {
        assertNotNull(DarkError)
    }

    // ── Assistant Colors (Dark) ──────────────────────────────────────────

    @Test
    fun `DarkAssistantBubble is defined`() {
        assertNotNull(DarkAssistantBubble)
    }

    @Test
    fun `DarkUserBubble is defined`() {
        assertNotNull(DarkUserBubble)
    }

    @Test
    fun `DarkOnAssistantBubble is defined`() {
        assertNotNull(DarkOnAssistantBubble)
    }

    @Test
    fun `DarkOnUserBubble is defined`() {
        assertNotNull(DarkOnUserBubble)
    }

    // ── Consistency checks ───────────────────────────────────────────────

    @Test
    fun `Light and Dark Primary are different colors`() {
        assertTrue("Light and Dark Primary should differ", Primary.value != DarkPrimary.value)
    }

    @Test
    fun `Light and Dark Background are different colors`() {
        assertTrue("Light and Dark Background should differ", Background.value != DarkBackground.value)
    }

    @Test
    fun `Background and Surface have same value`() {
        assertEquals("Background should equal Surface", Background.value, Surface.value)
    }

    @Test
    fun `DarkBackground and DarkSurface have same value`() {
        assertEquals("DarkBackground should equal DarkSurface", DarkBackground.value, DarkSurface.value)
    }

    @Test
    fun `OnBackground and OnSurface have same value`() {
        assertEquals("OnBackground should equal OnSurface", OnBackground.value, OnSurface.value)
    }

    @Test
    fun `UserBubble matches Primary color`() {
        assertEquals("UserBubble should match Primary", Primary.value, UserBubble.value)
    }

    @Test
    fun `DarkUserBubble matches DarkPrimary color`() {
        assertEquals("DarkUserBubble should match DarkPrimary", DarkPrimary.value, DarkUserBubble.value)
    }
}
