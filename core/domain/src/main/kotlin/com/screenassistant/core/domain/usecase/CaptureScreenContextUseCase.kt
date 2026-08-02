package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.model.ImageData
import com.screenassistant.core.domain.repository.ScreenContextRepository

class CaptureScreenContextUseCase(
    private val screenContextRepository: ScreenContextRepository
) {
    suspend fun captureScreenshot(): ImageData? {
        return screenContextRepository.captureScreenshot()
    }

    fun getScreenText(): String {
        return screenContextRepository.screenText.value
    }
}
