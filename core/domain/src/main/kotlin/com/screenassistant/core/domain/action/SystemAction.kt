package com.screenassistant.core.domain.action

import com.screenassistant.core.domain.model.ActionResult
import com.screenassistant.core.domain.model.SystemCommand

interface SystemAction {
    suspend fun execute(command: SystemCommand): ActionResult
}
