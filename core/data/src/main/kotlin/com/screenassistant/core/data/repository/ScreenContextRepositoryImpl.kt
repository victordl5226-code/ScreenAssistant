package com.screenassistant.core.data.repository

import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.repository.ScreenCaptureProvider
import com.screenassistant.core.domain.repository.ScreenContextRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenContextRepositoryImpl @Inject constructor(
    // B3: la captura se delega en el provider (core:data no conoce el servicio de
    // accesibilidad; la impl real es ServiceScreenCaptureProvider, inyectada en app).
    private val screenCaptureProvider: ScreenCaptureProvider
) : ScreenContextRepository {

    private val _screenText = MutableStateFlow("")
    override val screenText: StateFlow<String> = _screenText.asStateFlow()

    override suspend fun captureScreenshot(): ImageData? =
        screenCaptureProvider.captureScreenshot()

    override fun updateScreenText(text: String) {
        _screenText.value = text
    }
}
