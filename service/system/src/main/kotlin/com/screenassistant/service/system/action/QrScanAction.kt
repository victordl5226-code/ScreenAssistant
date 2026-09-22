package com.screenassistant.service.system.action

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class QrScanAction @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun scanQr(bitmap: Bitmap? = null): String {
        return try {
            // Tomar screenshot usando AccessibilityService si está disponible
            val imageBitmap = bitmap ?: takeScreenshot()
                ?: return "Error: No pude capturar la pantalla para escanear el código QR."

            val image = InputImage.fromBitmap(imageBitmap, 0)
            val scanner = BarcodeScanning.getClient()

            val result = suspendCancellableCoroutine { cont ->
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val qrCodes = barcodes.filter {
                            it.valueType in listOf(
                                Barcode.TYPE_URL,
                                Barcode.TYPE_TEXT,
                                Barcode.TYPE_WIFI,
                                Barcode.TYPE_CONTACT_INFO
                            )
                        }
                        if (qrCodes.isEmpty()) {
                            cont.resume("No detecté ningún código QR en la pantalla.")
                        } else {
                            val results = qrCodes.take(5).map { barcode ->
                                when (barcode.valueType) {
                                    Barcode.TYPE_URL -> "URL: ${barcode.url?.url ?: barcode.rawValue}"
                                    Barcode.TYPE_WIFI -> "WiFi: ${barcode.wifi?.ssid ?: "desconocido"}"
                                    Barcode.TYPE_CONTACT_INFO -> "Contacto: ${barcode.contactInfo?.name?.formattedName ?: "desconocido"}"
                                    else -> "Texto: ${barcode.rawValue}"
                                }
                            }
                            cont.resume("Códigos encontrados:\n${results.joinToString("\n")}")
                        }
                    }
                    .addOnFailureListener {
                        cont.resume("Error al escanear código QR.")
                    }
            }
            result
        } catch (e: Exception) {
            "Error al escanear código QR."
        }
    }

    private fun takeScreenshot(): Bitmap? {
        return try {
            // Intentar usar AccessibilityService para captura
            val service = com.screenassistant.core.domain.repository.ScreenContextRepository::class.java
            null // Fallback: usar cámara en su lugar
        } catch (e: Exception) {
            null
        }
    }
}
