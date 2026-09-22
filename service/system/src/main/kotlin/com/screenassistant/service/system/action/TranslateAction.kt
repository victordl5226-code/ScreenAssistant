package com.screenassistant.service.system.action

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class TranslateAction @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun translate(text: String, targetLanguage: com.screenassistant.core.domain.model.TranslateLanguage): String {
        if (text.isBlank()) {
            return "Error: ¿Qué texto quieres que traduzca?"
        }

        val mlKitTargetLanguage = when (targetLanguage) {
            com.screenassistant.core.domain.model.TranslateLanguage.SPANISH -> TranslateLanguage.SPANISH
            com.screenassistant.core.domain.model.TranslateLanguage.ENGLISH -> TranslateLanguage.ENGLISH
            com.screenassistant.core.domain.model.TranslateLanguage.FRENCH -> TranslateLanguage.FRENCH
            com.screenassistant.core.domain.model.TranslateLanguage.GERMAN -> TranslateLanguage.GERMAN
            com.screenassistant.core.domain.model.TranslateLanguage.PORTUGUESE -> TranslateLanguage.PORTUGUESE
            com.screenassistant.core.domain.model.TranslateLanguage.CHINESE -> TranslateLanguage.CHINESE
            com.screenassistant.core.domain.model.TranslateLanguage.JAPANESE -> TranslateLanguage.JAPANESE
        }

        // Detectar idioma del texto fuente automáticamente
        val sourceLanguage = detectLanguage(text)
            ?: return "Error: No pude detectar el idioma del texto."

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(mlKitTargetLanguage)
            .build()

        val translator = Translation.getClient(options)
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()

        return try {
            val result = suspendCancellableCoroutine { cont ->
                translator.downloadModelIfNeeded(conditions)
                    .addOnSuccessListener {
                        translator.translate(text)
                            .addOnSuccessListener { translatedText ->
                                cont.resume("Traducción: $translatedText")
                            }
                            .addOnFailureListener {
                                cont.resume("Error al traducir.")
                            }
                    }
                    .addOnFailureListener { e ->
                        cont.resume("Error: No pude descargar el modelo de traducción. Conectate a WiFi para la primera vez.")
                    }
            }
            result
        } catch (e: Exception) {
            "Error al traducir el texto."
        }
    }

    internal suspend fun detectLanguage(text: String): String? {
        return suspendCancellableCoroutine { cont ->
            val identifier: LanguageIdentifier = LanguageIdentification.getClient()
            identifier.identifyLanguage(text)
                .addOnSuccessListener { languageCode ->
                    if (languageCode == "und") {
                        cont.resume(null)
                    } else {
                        // Mapear códigos ML Kit a códigos TranslateLanguage
                        val mapped = mapToTranslateLanguage(languageCode)
                        cont.resume(mapped)
                    }
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }
    }

    internal fun mapToTranslateLanguage(languageCode: String): String {
        return when (languageCode) {
            "es" -> TranslateLanguage.SPANISH
            "en" -> TranslateLanguage.ENGLISH
            "fr" -> TranslateLanguage.FRENCH
            "de" -> TranslateLanguage.GERMAN
            "pt" -> TranslateLanguage.PORTUGUESE
            "zh" -> TranslateLanguage.CHINESE
            "ja" -> TranslateLanguage.JAPANESE
            "it" -> TranslateLanguage.ITALIAN
            "ru" -> TranslateLanguage.RUSSIAN
            "ar" -> TranslateLanguage.ARABIC
            "hi" -> TranslateLanguage.HINDI
            "ko" -> TranslateLanguage.KOREAN
            else -> TranslateLanguage.ENGLISH // fallback
        }
    }
}
