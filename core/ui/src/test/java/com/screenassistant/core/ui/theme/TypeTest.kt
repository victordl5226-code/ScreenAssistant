package com.screenassistant.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Tests unitarios para la tipografía del tema ScreenAssistant.
 *
 * Verifica que los estilos de texto estén definidos correctamente.
 */
class TypeTest {

    @Test
    fun `Typography object is defined`() {
        assertNotNull(Typography)
    }

    @Test
    fun `bodyLarge is defined with correct font size`() {
        val style = Typography.bodyLarge
        assertNotNull(style)
        assertEquals(16f, style.fontSize.value)
    }

    @Test
    fun `bodyLarge has normal weight`() {
        val style = Typography.bodyLarge
        assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, style.fontWeight)
    }

    @Test
    fun `bodyLarge uses default font family`() {
        val style = Typography.bodyLarge
        assertEquals(androidx.compose.ui.text.font.FontFamily.Default, style.fontFamily)
    }

    @Test
    fun `titleLarge is defined with correct font size`() {
        val style = Typography.titleLarge
        assertNotNull(style)
        assertEquals(22f, style.fontSize.value)
    }

    @Test
    fun `titleLarge has bold weight`() {
        val style = Typography.titleLarge
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, style.fontWeight)
    }

    @Test
    fun `labelSmall is defined with correct font size`() {
        val style = Typography.labelSmall
        assertNotNull(style)
        assertEquals(11f, style.fontSize.value)
    }

    @Test
    fun `labelSmall has medium weight`() {
        val style = Typography.labelSmall
        assertEquals(androidx.compose.ui.text.font.FontWeight.Medium, style.fontWeight)
    }

    @Test
    fun `titleLarge is larger than bodyLarge`() {
        assertTrue(Typography.titleLarge.fontSize > Typography.bodyLarge.fontSize)
    }

    @Test
    fun `bodyLarge is larger than labelSmall`() {
        assertTrue(Typography.bodyLarge.fontSize > Typography.labelSmall.fontSize)
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
