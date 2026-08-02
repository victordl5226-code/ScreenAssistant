package com.screenassistant.core.domain.repository

import com.screenassistant.core.domain.model.ImageData
import kotlinx.coroutines.flow.StateFlow

interface ScreenContextRepository {
    val screenText: StateFlow<String>
    suspend fun captureScreenshot(): ImageData?
    fun updateScreenText(text: String)
}
