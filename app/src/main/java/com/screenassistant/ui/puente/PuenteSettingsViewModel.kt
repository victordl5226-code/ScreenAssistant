package com.screenassistant.ui.puente

import androidx.lifecycle.ViewModel
import com.screenassistant.core.data.util.PuenteConfig
import com.screenassistant.core.data.util.PuenteConfigStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PuenteUiState(
    /** F2: paquete del receptor de la respuesta (vacío = default taskerm al cargar, H1). */
    val packageRespuesta: String = "",
    /** F3: key de AutoRemote (campo enmascarado en la UI). */
    val autoRemoteKey: String = "",
    /** F1 v1.2: token compartido del canal (enmascarado; vacío = canal abierto). */
    val tokenCompartido: String = "",
    /** F3 v1.2: switch explícito de URL callback (default OFF — cero llamadas URL sorpresa). */
    val enviarRespuestaURL: Boolean = false,
    /** Fallback de prefs del store expuesto (patrón ApiKeyViewModel). */
    val isDegraded: Boolean = false,
)

// v1.2a (hallazgo H1): ELIMINADO el enum PuenteError y el campo `error` del estado.
// Sin ALLOWLIST_SIN_PAQUETES el enum queda VACÍO (el token blank es un estado
// legítimo "canal abierto" → no hay validación de UI que pueda fallar) — un enum
// sin entries y su estado de error serían código muerto/basura (ADR-015 §5.3).

/**
 * ViewModel de la sección del puente (P4, ADR-015 v1.2 — M4): patrón
 * ApiKeyViewModel 1:1 — refresh()/save(), sin R.string. Guarda en la config
 * compartida del puente (UNA fuente: handler F1, emitter F2, callback F3, UI).
 */
@HiltViewModel
class PuenteSettingsViewModel @Inject constructor(
    private val configStore: PuenteConfigStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PuenteUiState())
    val uiState: StateFlow<PuenteUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Público (patrón M5 de ApiKey): re-lee el estado tras cambios externos. */
    fun refresh() {
        val config = configStore.cargar()
        _uiState.value = PuenteUiState(
            packageRespuesta = config.packageRespuesta ?: "",
            autoRemoteKey = config.autoRemoteKey,
            tokenCompartido = config.tokenCompartido,
            enviarRespuestaURL = config.enviarRespuestaURL,
            isDegraded = configStore.isUsingFallback,
        )
    }

    fun onTokenChange(texto: String) {
        _uiState.value = _uiState.value.copy(tokenCompartido = texto)
    }

    fun onUrlFlagChange(activado: Boolean) {
        _uiState.value = _uiState.value.copy(enviarRespuestaURL = activado)
    }

    fun onPackageRespuestaChange(texto: String) {
        _uiState.value = _uiState.value.copy(packageRespuesta = texto)
    }

    fun onAutoRemoteKeyChange(texto: String) {
        _uiState.value = _uiState.value.copy(autoRemoteKey = texto)
    }

    /**
     * Guarda la config. v1.2: el token se TRIMEA al guardar (la comparación del
     * handler ya trima, pero la UI normaliza antes de persistir — diseño §5.1);
     * blank = canal abierto (estado legítimo, sin validación de UI). H1:
     * packageRespuesta blank → se persiste null (crudo; cargar() materializa el
     * default taskerm). La key se trimea (intacto de v1.1).
     */
    fun save() {
        val current = _uiState.value
        configStore.guardar(
            PuenteConfig(
                packageRespuesta = current.packageRespuesta.trim().takeIf { it.isNotBlank() },
                autoRemoteKey = current.autoRemoteKey.trim(),
                tokenCompartido = current.tokenCompartido.trim(),
                enviarRespuestaURL = current.enviarRespuestaURL,
            )
        )
        refresh()
    }

    /** Borra SOLO la key de AutoRemote conservando el resto de la config. */
    fun clearKey() {
        val config = configStore.cargar()
        configStore.guardar(config.copy(autoRemoteKey = ""))
        refresh()
    }
}
