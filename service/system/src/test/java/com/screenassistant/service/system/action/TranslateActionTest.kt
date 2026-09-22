package com.screenassistant.service.system.action

import com.google.mlkit.nl.translate.TranslateLanguage as MLKitTranslateLanguage
import com.screenassistant.core.domain.model.TranslateLanguage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranslateActionTest {

    private lateinit var action: TranslateAction

    @Before
    fun setup() {
        action = TranslateAction(mockk(relaxed = true))
    }

    @Test
    fun `texto vacio devuelve error`() = runTest {
        val result = action.translate("", TranslateLanguage.ENGLISH)
        assertEquals("Error: ¿Qué texto quieres que traduzca?", result)
    }

    @Test
    fun `texto solo espacios devuelve error`() = runTest {
        val result = action.translate("   ", TranslateLanguage.ENGLISH)
        assertEquals("Error: ¿Qué texto quieres que traduzca?", result)
    }

    @Test
    fun `mapToTranslateLanguage mapea códigos soportados`() {
        assertEquals(MLKitTranslateLanguage.SPANISH, action.mapToTranslateLanguage("es"))
        assertEquals(MLKitTranslateLanguage.ENGLISH, action.mapToTranslateLanguage("en"))
        assertEquals(MLKitTranslateLanguage.FRENCH, action.mapToTranslateLanguage("fr"))
        assertEquals(MLKitTranslateLanguage.GERMAN, action.mapToTranslateLanguage("de"))
        assertEquals(MLKitTranslateLanguage.PORTUGUESE, action.mapToTranslateLanguage("pt"))
        assertEquals(MLKitTranslateLanguage.CHINESE, action.mapToTranslateLanguage("zh"))
        assertEquals(MLKitTranslateLanguage.JAPANESE, action.mapToTranslateLanguage("ja"))
        assertEquals(MLKitTranslateLanguage.ITALIAN, action.mapToTranslateLanguage("it"))
        assertEquals(MLKitTranslateLanguage.RUSSIAN, action.mapToTranslateLanguage("ru"))
        assertEquals(MLKitTranslateLanguage.ARABIC, action.mapToTranslateLanguage("ar"))
        assertEquals(MLKitTranslateLanguage.HINDI, action.mapToTranslateLanguage("hi"))
        assertEquals(MLKitTranslateLanguage.KOREAN, action.mapToTranslateLanguage("ko"))
        assertEquals(MLKitTranslateLanguage.ENGLISH, action.mapToTranslateLanguage("sv")) // fallback
    }

    @Test
    fun `mapToTranslateLanguage case sensitive solo lowercase`() {
        assertEquals(MLKitTranslateLanguage.SPANISH, action.mapToTranslateLanguage("es"))
        assertEquals(MLKitTranslateLanguage.ENGLISH, action.mapToTranslateLanguage("en"))
        assertEquals(MLKitTranslateLanguage.FRENCH, action.mapToTranslateLanguage("fr"))
        // Mayúsculas usan fallback
        assertEquals(MLKitTranslateLanguage.ENGLISH, action.mapToTranslateLanguage("ES"))
        assertEquals(MLKitTranslateLanguage.ENGLISH, action.mapToTranslateLanguage("EN"))
        assertEquals(MLKitTranslateLanguage.ENGLISH, action.mapToTranslateLanguage("FR"))
    }

    @Test
    fun `mapToTranslateLanguage códigos vacíos y nulos usan fallback`() {
        assertEquals(MLKitTranslateLanguage.ENGLISH, action.mapToTranslateLanguage(""))
        assertEquals(MLKitTranslateLanguage.ENGLISH, action.mapToTranslateLanguage("xx"))
        assertEquals(MLKitTranslateLanguage.ENGLISH, action.mapToTranslateLanguage("xyz"))
    }

    }