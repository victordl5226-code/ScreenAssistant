package com.screenassistant.service.system.action

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OcrActionTest {

    private lateinit var action: OcrAction

    @Before
    fun setup() {
        action = OcrAction(mockk(relaxed = true))
    }

    @Test
    fun `scanText sin screenshot devuelve error captura`() = runTest {
        val result = action.scanText()
        assertEquals("Error: No pude capturar la pantalla para leer el texto.", result)
    }
}