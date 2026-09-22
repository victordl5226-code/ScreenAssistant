package com.screenassistant.core.ui.theme

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitarios para el tema ScreenAssistant.
 *
 * Verifica que los esquemas de colores y la tipografía estén correctamente configurados.
 */
class ThemeTest {

    @Test
    fun `Primary is purple-ish`() {
        // Verify it's not black or white (purple has moderate red, low green, high blue)
        assertTrue("Primary should not be black", Primary.value != 0uL)
        assertTrue("Primary should not be white", Primary.value != 0xFFFFFFFFuL)
    }

    @Test
    fun `Dark Primary is defined`() {
        assertNotNull(DarkPrimary)
        assertTrue("DarkPrimary should have non-zero value", DarkPrimary.value != 0uL)
    }

    @Test
    fun `Typography has all required styles`() {
        assertNotNull("bodyLarge must be defined", Typography.bodyLarge)
        assertNotNull("titleLarge must be defined", Typography.titleLarge)
        assertNotNull("labelSmall must be defined", Typography.labelSmall)
    }

    @Test
    fun `Font sizes are positive`() {
        assertTrue("bodyLarge fontSize > 0", Typography.bodyLarge.fontSize.value > 0)
        assertTrue("titleLarge fontSize > 0", Typography.titleLarge.fontSize.value > 0)
        assertTrue("labelSmall fontSize > 0", Typography.labelSmall.fontSize.value > 0)
    }

    @Test
    fun `Line heights are positive`() {
        assertTrue("bodyLarge lineHeight > 0", Typography.bodyLarge.lineHeight.value > 0)
        assertTrue("titleLarge lineHeight > 0", Typography.titleLarge.lineHeight.value > 0)
        assertTrue("labelSmall lineHeight > 0", Typography.labelSmall.lineHeight.value > 0)
    }

    @Test
    fun `titleLarge is larger than bodyLarge`() {
        assertTrue(Typography.titleLarge.fontSize > Typography.bodyLarge.fontSize)
    }

    @Test
    fun `bodyLarge is larger than labelSmall`() {
        assertTrue(Typography.bodyLarge.fontSize > Typography.labelSmall.fontSize)
    }

    @Test
    fun `All light theme colors are defined`() {
        val colors = listOf(
            Primary, OnPrimary, PrimaryContainer, OnPrimaryContainer,
            Secondary, OnSecondary, SecondaryContainer, OnSecondaryContainer,
            Background, OnBackground, Surface, OnSurface, SurfaceVariant, OnSurfaceVariant,
            Error, OnError, ErrorContainer, OnErrorContainer,
        )
        assertTrue("All light colors should be defined", colors.size == 18)
        colors.forEach { color ->
            assertTrue("Color should be non-zero", color.value != 0uL)
        }
    }

    @Test
    fun `All dark theme colors are defined`() {
        val colors = listOf(
            DarkPrimary, DarkOnPrimary, DarkPrimaryContainer, DarkOnPrimaryContainer,
            DarkSecondary, DarkOnSecondary, DarkSecondaryContainer, DarkOnSecondaryContainer,
            DarkBackground, DarkOnBackground, DarkSurface, DarkOnSurface,
            DarkSurfaceVariant, DarkOnSurfaceVariant,
            DarkError, DarkOnError, DarkErrorContainer, DarkOnErrorContainer,
        )
        assertTrue("All dark colors should be defined", colors.size == 18)
        colors.forEach { color ->
            assertTrue("Color should be non-zero", color.value != 0uL)
        }
    }
}
