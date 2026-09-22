package com.screenassistant.ui.apikey

import androidx.lifecycle.ViewModel
import com.screenassistant.core.data.util.ApiKeyProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class UiState(
    val input: String = "",
    val isConfigured: Boolean = false,
    val maskedKey: String = "",
    val isDegraded: Boolean = false,
    val error: ErrorType? = null
)

// Sin R.string: enum testeable en JVM, el mapeo a texto se hace en la UI.
enum class ErrorType { KEY_EMPTY, KEY_INVALID }

@HiltViewModel
class ApiKeyViewModel @Inject constructor(
    private val apiKeyProvider: ApiKeyProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    // Público (M5): permite re-leer el estado tras cambios externos.
    // Orden R5: getApiKey() primero (dispara el lazy de prefs que puede
    // marcar isUsingFallback) y solo después se lee isUsingFallback.
    fun refresh() {
        val key = apiKeyProvider.getApiKey()
        val current = _uiState.value
        _uiState.value = current.copy(
            isConfigured = key.isNotBlank(),
            maskedKey = mask(key),
            isDegraded = apiKeyProvider.isUsingFallback
        )
    }

    fun onInputChange(newValue: String) {
        _uiState.value = _uiState.value.copy(input = newValue, error = null)
    }

    fun saveKey(rawKey: String) {
        val trimmed = rawKey.trim()
        if (trimmed.isBlank()) {
            _uiState.value = _uiState.value.copy(error = ErrorType.KEY_EMPTY)
            return
        }
        // El prefijo ya NO se valida: Google migró el formato de las API keys
        // de Gemini ("AIza..." → "AQ.Ab..."). Solo se rechazan los espacios
        // internos, que son el error de pegado más común.
        if (trimmed.any { it.isWhitespace() }) {
            _uiState.value = _uiState.value.copy(error = ErrorType.KEY_INVALID)
            return
        }

        apiKeyProvider.storeApiKey(trimmed)
        // V1 DBG-veredicto: re-lee el estado REAL en vez de marcar isConfigured
        // a ciegas (contrato fail-soft M18: storeApiKey puede tragar fallos).
        // refresh() deja isConfigured/maskedKey/isDegraded desde la leída;
        // aquí solo se limpia input/error preservando ese estado real.
        refresh()
        _uiState.value = _uiState.value.copy(input = "", error = null)
    }

    fun clearKey() {
        apiKeyProvider.clearApiKey()
        refresh()
    }

    // M3: "•••• " + últimos 4 chars; key de <=4 chars → "•••• " + key entera.
    private fun mask(key: String): String =
        if (key.isBlank()) "" else "•••• " + key.takeLast(4)
}
