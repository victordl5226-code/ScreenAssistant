package com.screenassistant.service.system.action

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class QrScanActionTest {

    private lateinit var action: QrScanAction

    @Before
    fun setup() {
        // QrScanAction toma screenshot internamente que siempre retorna null
        // Test verifica el comportamiento actual (placeholder)
        action = QrScanAction(mockk(relaxed = true))
    }

    @Test
    fun `scanQr sin screenshot devuelve error captura`() = runTest {
        val result = action.scanQr()
        assertEquals("Error: No pude capturar la pantalla para escanear el código QR.", result)
    }
}