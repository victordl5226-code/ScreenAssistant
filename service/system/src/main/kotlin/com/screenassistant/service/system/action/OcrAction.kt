package com.screenassistant.service.system.action

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class OcrAction @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun scanText(bitmap: Bitmap? = null): String {
        return try {
            val imageBitmap = bitmap ?: takeScreenshot()
                ?: return "Error: No pude capturar la pantalla para leer el texto."

            val image = InputImage.fromBitmap(imageBitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            val result = suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val text = visionText.text
                        if (text.isBlank()) {
                            cont.resume("No detecté texto en la pantalla.")
                        } else {
                            val blocks = visionText.textBlocks.size
                            val preview = text.take(500)
                            if (text.length > 500) {
                                cont.resume("Texto detectado ($blocks bloques, ${text.length} caracteres):\n$preview...")
                            } else {
                                cont.resume("Texto detectado:\n$text")
                            }
                        }
                    }
                    .addOnFailureListener {
                        cont.resume("Error al leer texto.")
                    }
            }
            result
        } catch (e: Exception) {
            "Error al leer texto de la pantalla."
        }
    }

    private fun takeScreenshot(): Bitmap? {
        return try {
            null // Fallback: usar cámara en su lugar
        } catch (e: Exception) {
            null
        }
    }
}
