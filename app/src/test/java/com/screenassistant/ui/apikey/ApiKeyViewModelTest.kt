package com.screenassistant.ui.apikey

import com.screenassistant.core.data.util.ApiKeyProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiKeyViewModelTest {

    private lateinit var apiKeyProvider: ApiKeyProvider
    private lateinit var viewModel: ApiKeyViewModel

    // Mini-fake del contrato real de ApiKeyProvider (patrón STORED-MUTABLE, B1)
    private var storedKey: String = ""

    @Before
    fun setup() {
        storedKey = ""
        apiKeyProvider = mockk()
        every { apiKeyProvider.getApiKey() } answers { storedKey }
        every { apiKeyProvider.storeApiKey(any()) } answers { storedKey = firstArg() }
        every { apiKeyProvider.clearApiKey() } answers { storedKey = "" }
        every { apiKeyProvider.isUsingFallback } returns false
        viewModel = ApiKeyViewModel(apiKeyProvider)
    }

    // 1. Estado inicial configurado: key guardada se refleja enmascarada
    @Test
    fun `estado inicial con key guardada muestra isConfigured y maskedKey`() {
        storedKey = "AIzaTestKey123"
        viewModel.refresh()

        val state = viewModel.uiState.value
        assertTrue(state.isConfigured)
        assertEquals("•••• y123", state.maskedKey)
        assertNull(state.error)
    }

    // 2. Estado inicial vacío
    @Test
    fun `estado inicial sin key muestra no configurado`() {
        val state = viewModel.uiState.value
        assertFalse(state.isConfigured)
        assertEquals("", state.maskedKey)
        assertNull(state.error)
    }

    // 3. Modo degradado: isUsingFallback se refleja en isDegraded
    @Test
    fun `modo degradado marca isDegraded y llama getApiKey`() {
        every { apiKeyProvider.isUsingFallback } returns true
        viewModel.refresh()

        assertTrue(viewModel.uiState.value.isDegraded)
        verify { apiKeyProvider.getApiKey() }
    }

    // 4. saveKey exitoso: guarda el trim y actualiza el estado completo
    @Test
    fun `saveKey exitoso guarda key recortada y estado completo`() {
        viewModel.saveKey("  AQ.AbNuevaKey  ")

        verify(exactly = 1) { apiKeyProvider.storeApiKey(eq("AQ.AbNuevaKey")) }
        verify(exactly = 0) { apiKeyProvider.clearApiKey() }
        val state = viewModel.uiState.value
        assertTrue(state.isConfigured)
        assertEquals("•••• aKey", state.maskedKey)
        assertFalse(state.isDegraded)
        assertNull(state.error)
        assertEquals("", state.input)
    }

    // 5. Trim de espacios externos
    @Test
    fun `saveKey recorta espacios externos`() {
        viewModel.saveKey(" AQ.AbTrimKey ")

        verify(exactly = 1) { apiKeyProvider.storeApiKey(eq("AQ.AbTrimKey")) }
    }

    // 6. Key vacía: error KEY_EMPTY y cero efectos en el provider
    @Test
    fun `saveKey vacia produce KEY_EMPTY sin tocar el provider`() {
        viewModel.saveKey("   ")

        assertEquals(ErrorType.KEY_EMPTY, viewModel.uiState.value.error)
        verify(exactly = 0) { apiKeyProvider.storeApiKey(any()) }
        verify(exactly = 0) { apiKeyProvider.clearApiKey() }
    }

    // 7. Formato nuevo AQ.Ab (sin prefijo AIza) es VÁLIDO: Google migró el formato
    @Test
    fun `saveKey con formato AQAb sin prefijo AIza es valida`() {
        viewModel.saveKey("AQ.AbNuevaKey")

        verify(exactly = 1) { apiKeyProvider.storeApiKey(eq("AQ.AbNuevaKey")) }
        assertNull(viewModel.uiState.value.error)
    }

    // 8. clearKey: elimina la key guardada y queda no configurado
    @Test
    fun `clearKey elimina la key guardada`() {
        storedKey = "AIzaKey"
        viewModel.refresh()
        assertTrue(viewModel.uiState.value.isConfigured)

        viewModel.clearKey()

        verify(exactly = 1) { apiKeyProvider.clearApiKey() }
        verify(exactly = 0) { apiKeyProvider.storeApiKey(any()) }
        assertFalse(viewModel.uiState.value.isConfigured)
    }

    // 9. refresh tras store externo: refleja la key guardada por otro proceso
    @Test
    fun `refresh refleja cambios externos de la key`() {
        storedKey = "AIzaExterna"
        viewModel.refresh()

        assertTrue(viewModel.uiState.value.isConfigured)
        assertEquals("•••• erna", viewModel.uiState.value.maskedKey)
    }

    // 10. Espacios internos → KEY_INVALID (independiente del formato de prefijo)
    @Test
    fun `saveKey con espacios internos produce KEY_INVALID`() {
        viewModel.saveKey("AQ.Ab Key Con Espacio")

        assertEquals(ErrorType.KEY_INVALID, viewModel.uiState.value.error)
        verify(exactly = 0) { apiKeyProvider.storeApiKey(any()) }
    }

    // 11. onInputChange limpia el error y actualiza el input
    @Test
    fun `onInputChange limpia el error anterior y actualiza el texto`() {
        viewModel.saveKey("AIza Key")
        assertEquals(ErrorType.KEY_INVALID, viewModel.uiState.value.error)

        viewModel.onInputChange("AQ.AbNueva")

        val state = viewModel.uiState.value
        assertNull(state.error)
        assertEquals("AQ.AbNueva", state.input)
    }

    // 12. Case borde M3: key de <=4 chars se muestra entera tras el prefijo
    @Test
    fun `maskedKey con key corta muestra la key entera`() {
        storedKey = "AIza"
        viewModel.refresh()

        assertTrue(viewModel.uiState.value.isConfigured)
        assertEquals("•••• AIza", viewModel.uiState.value.maskedKey)
    }
}
