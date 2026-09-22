package com.screenassistant.service.system.action

import com.screenassistant.core.domain.model.AssistantLanguage
import com.screenassistant.core.domain.repository.GeminiRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LanguageActionTest {

    private lateinit var geminiRepository: GeminiRepository
    private lateinit var lazyRepository: dagger.Lazy<GeminiRepository>
    private lateinit var action: LanguageAction

    @Before
    fun setup() {
        geminiRepository = mockk(relaxed = true)
        lazyRepository = object : dagger.Lazy<GeminiRepository> {
            override fun get(): GeminiRepository = geminiRepository
        }
        action = LanguageAction(lazyRepository)
    }

    @Test
    fun `setLanguage ENGLISH retorna mensaje en ingles`() {
        val result = action.setLanguage(AssistantLanguage.ENGLISH)
        assertTrue(result.contains("inglés"))
    }

    @Test
    fun `setLanguage SPANISH retorna mensaje en espanol`() {
        val result = action.setLanguage(AssistantLanguage.SPANISH)
        assertTrue(result.contains("español"))
    }

    @Test
    fun `setLanguage ENGLISH delega en geminiRepository`() {
        action.setLanguage(AssistantLanguage.ENGLISH)
        coVerify(exactly = 1) { geminiRepository.setLanguage(AssistantLanguage.ENGLISH) }
    }

    @Test
    fun `setLanguage SPANISH delega en geminiRepository`() {
        action.setLanguage(AssistantLanguage.SPANISH)
        coVerify(exactly = 1) { geminiRepository.setLanguage(AssistantLanguage.SPANISH) }
    }

    @Test
    fun `setLanguage retorna exito`() {
        val result = action.setLanguage(AssistantLanguage.ENGLISH)
        assertTrue(result.startsWith("Éxito"))
    }
}
