package com.screenassistant.core.domain.action

import com.screenassistant.core.domain.model.ActionResult
import com.screenassistant.core.domain.model.AssistantMode
import com.screenassistant.core.domain.model.SystemCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SystemAction {
    suspend fun execute(command: SystemCommand): ActionResult
    
    // J.A.R.V.I.S. v3.5: Flujo de estado del modo del asistente
    val assistantMode: StateFlow<AssistantMode>
}
