package com.screenassistant.service.system.action

import com.screenassistant.core.domain.model.AssistantLanguage
import com.screenassistant.core.domain.repository.GeminiRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanguageAction @Inject constructor(
    // Lazy rompe el ciclo Dagger: LanguageAction → GeminiRepository → SystemAction → SystemActionHandler → LanguageAction
    private val geminiRepository: dagger.Lazy<GeminiRepository>
) {
    fun setLanguage(language: AssistantLanguage): String {
        geminiRepository.get().setLanguage(language)
        return if (language == AssistantLanguage.ENGLISH) {
            "Éxito: Entendido. A partir de ahora hablaré en inglés."
        } else {
            "Éxito: Entendido. A partir de ahora hablaré en español."
        }
    }
}
