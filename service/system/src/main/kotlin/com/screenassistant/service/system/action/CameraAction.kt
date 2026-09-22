package com.screenassistant.service.system.action

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraAction @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun takePhoto(useFrontCamera: Boolean): String {
        return try {
            val intent = createImageCaptureIntent(useFrontCamera)
            if (intent.resolveActivity(context.packageManager) == null) {
                return "Error: No hay aplicación de cámara disponible."
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            val camera = if (useFrontCamera) "frontal" else "trasera"
            "Abriendo cámara $camera para tomar una foto."
        } catch (e: SecurityException) {
            "Error: No tengo permiso para usar la cámara."
        } catch (e: Exception) {
            "Error: No se pudo abrir la cámara."
        }
    }

    private fun createImageCaptureIntent(useFrontCamera: Boolean): Intent {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        // Solicitar cámara frontal o trasera
        if (useFrontCamera) {
            intent.putExtra(
                android.hardware.camera2.CameraCharacteristics.LENS_FACING.toString(),
                android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
            )
        }

        // Crear archivo temporal para la foto
        val imageUri = createImageUri()
        if (imageUri != null) {
            intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
        }

        return intent
    }

    private fun createImageUri(): Uri? {
        return try {
            val fileName = "IMG_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ScreenAssistant")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
        } catch (e: Exception) {
            null
        }
    }
}
