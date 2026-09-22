package com.screenassistant.service.system.action

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FaceDetectionActionTest {

    private lateinit var action: FaceDetectionAction

    @Before
    fun setup() {
        action = FaceDetectionAction(mockk(relaxed = true))
    }

    @Test
    fun `detectFace sin screenshot devuelve error captura`() = runTest {
        val result = action.detectFace()
        assertEquals("Error: No pude capturar la pantalla para detectar caras.", result)
    }
}