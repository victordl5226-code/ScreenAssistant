package com.screenassistant.core.domain.model

sealed class ActionResult {
    data class Success(val message: String) : ActionResult()
    data class Error(val reason: String) : ActionResult()
}
