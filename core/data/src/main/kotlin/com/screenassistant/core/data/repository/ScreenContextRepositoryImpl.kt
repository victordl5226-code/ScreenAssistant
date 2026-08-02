package com.screenassistant.core.data.repository

import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.repository.ScreenContextRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenContextRepositoryImpl @Inject constructor() : ScreenContextRepository {

    private val _screenText = MutableStateFlow("")
    override val screenText: StateFlow<String> = _screenText.asStateFlow()

    override suspend fun captureScreenshot(): ImageData? {
        // El screenshot se maneja desde ScreenContextService (servicio de accesibilidad)
        // Esta implementación delega en el servicio; el repositorio es solo el contenedor de estado
        return null
    }

    override fun updateScreenText(text: String) {
        _screenText.value = text
    }
}
