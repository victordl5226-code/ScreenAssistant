package com.screenassistant.ui.puente

import com.screenassistant.core.data.util.PuenteConfig
import com.screenassistant.core.data.util.PuenteConfigStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PuenteSettingsViewModel (P4, ADR-015 v1.2 — M4): patrón ApiKeyViewModelTest con
 * MockK de PuenteConfigStore (fake mutable STORED-MUTABLE: guardar actualiza la
 * var config que cargar devuelve — roundtrip real sin verificación circular).
 *
 * v1.2a (enmienda H1): ELIMINADOS T3/T4/T9/T10 (usaban onAllowlistToggle /
 * onPaquetesChange / PuenteError.ALLOWLIST_SIN_PAQUETES — todo muerto con la
 * allowlist). EDITADOS 6 (T1/T2 adaptados a token/flag; T5/T6/T7 mínimos; T8
 * PuenteConfig sin allowlist). +2 NUEVOS (flag reflejado antes de save; roundtrip
 * token trimeado + flag). Suite = 8.
 */
class PuenteSettingsViewModelTest {

    private lateinit var configStore: PuenteConfigStore
    private lateinit var viewModel: PuenteSettingsViewModel

    // Mini-fake del contrato real del store (patrón STORED-MUTABLE, B1)
    private var config: PuenteConfig = PuenteConfig()

    @Before
    fun setup() {
        config = PuenteConfig()
        configStore = mockk()
        every { configStore.cargar() } answers { config }
        every { configStore.guardar(any()) } answers { config = firstArg() }
        every { configStore.isUsingFallback } returns false
        viewModel = PuenteSettingsViewModel(configStore)
    }

    // 1. refresh carga la config del store (T1 editado: token/flag en lugar de allowlist)
    @Test
    fun `refresh carga la config del store al estado`() {
        config = PuenteConfig(
            packageRespuesta = "com.otro.paquete",
            autoRemoteKey = "key-1",
            tokenCompartido = "misecreto",
            enviarRespuestaURL = true,
        )

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals("com.otro.paquete", state.packageRespuesta)
        assertEquals("key-1", state.autoRemoteKey)
        assertEquals("misecreto", state.tokenCompartido)
        assertTrue(state.enviarRespuestaURL)
    }

    // 2. save guarda la config completa (T2 editado: onTokenChange/onUrlFlagChange)
    @Test
    fun `save guarda la config con los campos del estado`() {
        viewModel.onTokenChange("misecreto")
        viewModel.onUrlFlagChange(true)
        viewModel.onPackageRespuestaChange("com.otro.paquete")
        viewModel.onAutoRemoteKeyChange("key-1")

        viewModel.save()

        verify(exactly = 1) { configStore.guardar(any()) }
        assertEquals("com.otro.paquete", config.packageRespuesta)
        assertEquals("key-1", config.autoRemoteKey)
        assertEquals("misecreto", config.tokenCompartido)
        assertTrue(config.enviarRespuestaURL)
    }

    // 5. packageRespuesta blank → se guarda null (crudo — H1)
    @Test
    fun `save con packageRespuesta blank guarda null`() {
        viewModel.onPackageRespuestaChange("   ")

        viewModel.save()

        assertTrue(config.packageRespuesta == null)
    }

    // 6. key con espacios → trim
    @Test
    fun `save recorta la key de AutoRemote`() {
        viewModel.onAutoRemoteKeyChange("  Mi Key  ")

        viewModel.save()

        assertEquals("Mi Key", config.autoRemoteKey)
    }

    // 7. refresh con fallback → isDegraded expuesto
    @Test
    fun `refresh con fallback expone isDegraded`() {
        every { configStore.isUsingFallback } returns true

        viewModel.refresh()

        assertTrue(viewModel.uiState.value.isDegraded)
    }

    // 8. clearKey conserva el resto de la config (T8 editado: sin allowlist)
    @Test
    fun `clearKey borra solo la key conservando packageRespuesta token y flag`() {
        config = PuenteConfig(
            packageRespuesta = "com.otro.paquete",
            autoRemoteKey = "key-1",
            tokenCompartido = "misecreto",
            enviarRespuestaURL = true,
        )
        viewModel.refresh()

        viewModel.clearKey()

        assertEquals("com.otro.paquete", config.packageRespuesta)
        assertEquals("", config.autoRemoteKey)
        assertEquals("misecreto", config.tokenCompartido)
        assertTrue(config.enviarRespuestaURL)
    }

    // NUEVO (a): onUrlFlagChange se refleja en el estado ANTES de save
    @Test
    fun `onUrlFlagChange true y false se reflejan en el estado antes de save`() {
        viewModel.onUrlFlagChange(true)
        assertTrue(viewModel.uiState.value.enviarRespuestaURL)

        viewModel.onUrlFlagChange(false)
        assertFalse(viewModel.uiState.value.enviarRespuestaURL)
    }

    // NUEVO (b): refresh tras save devuelve token trimeado y flag persistidos
    // (roundtrip real con el fake STORED-MUTABLE — cubre el trim del token)
    @Test
    fun `refresh tras save devuelve token trimeado y flag persistidos`() {
        viewModel.onTokenChange("  misecreto  ")
        viewModel.onUrlFlagChange(true)
        viewModel.save()

        // save() llama refresh() → el estado viene del store fake (roundtrip real).
        assertEquals("misecreto", viewModel.uiState.value.tokenCompartido)
        assertTrue(viewModel.uiState.value.enviarRespuestaURL)
        // El store guardó la versión trimeada (no la cruda con espacios).
        assertEquals("misecreto", config.tokenCompartido)
    }
}
