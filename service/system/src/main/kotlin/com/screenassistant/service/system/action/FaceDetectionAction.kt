package com.screenassistant.service.system.action

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class FaceDetectionAction @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun detectFace(bitmap: Bitmap? = null): String {
        return try {
            val imageBitmap = bitmap ?: takeScreenshot()
                ?: return "Error: No pude capturar la pantalla para detectar caras."

            val image = InputImage.fromBitmap(imageBitmap, 0)
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .build()

            val detector = FaceDetection.getClient(options)

            val result = suspendCancellableCoroutine { cont ->
                detector.process(image)
                    .addOnSuccessListener { faces ->
                        if (faces.isEmpty()) {
                            cont.resume("No detecté caras en la pantalla.")
                        } else {
                            val summaries = faces.take(5).mapIndexed { index, face ->
                                val bounds = face.boundingBox
                                val smile = if (face.smilingProbability != null) {
                                    val prob = face.smilingProbability!!
                                    when {
                                        prob > 0.7f -> "sonriendo"
                                        prob > 0.3f -> "neutral"
                                        else -> "serio"
                                    }
                                } else "expresión desconocida"

                                "Cara ${index + 1}: $smile (${bounds.width()}x${bounds.height()}px)"
                            }
                            cont.resume("Caras detectadas (${faces.size}):\n${summaries.joinToString("\n")}")
                        }
                    }
                    .addOnFailureListener {
                        cont.resume("Error al detectar caras.")
                    }
            }
            result
        } catch (e: Exception) {
            "Error al detectar caras."
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
