package com.screenassistant.core.domain.usecase

import com.screenassistant.core.domain.model.TemporalContext
import com.screenassistant.core.domain.repository.TemporalRepository
import java.time.Clock
import javax.inject.Inject

/**
 * Caso de uso para obtener el contexto temporal actual.
 *
 * Calcula el contexto temporal usando el repositorio configurado.
 * El Clock se inyecta para permitir testing determinístico.
 *
 * @property repository Repositorio de contexto temporal
 */
class GetTemporalContextUseCase @Inject constructor(
    private val repository: TemporalRepository
) {
    /**
     * Obtiene el contexto temporal actual.
     *
     * @param clock Reloj inyectable (default: zona por defecto del sistema)
     * @return TemporalContext con hora, día, período, etc.
     */
    operator fun invoke(clock: Clock = Clock.systemDefaultZone()): TemporalContext {
        return repository.getCurrentContext(clock)
    }
}
