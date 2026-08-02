package com.screenassistant.core.domain.model

/**
 * Marcadores de UI que el parser devuelve SIN pasar por SystemActionHandler:
 * el handler es service:system y no tiene acceso al estado del overlay.
 * OverlayViewModel los intercepta antes de handleResponse.
 */
object CommandMarkers {
    const val HELP = "__SCREEN_ASSISTANT_HELP__"
    const val REPEAT = "__SCREEN_ASSISTANT_REPEAT__"
}
