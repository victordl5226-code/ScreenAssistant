package com.screenassistant.core.domain.bridge

/**
 * ADR-B7 (Lote 8): resultado de una acción para el wire del puente Tasker.
 *
 * El wire distingue TRES casos (el emisor decide qué hacer con cada uno):
 *  - "ok" + mensaje → acción completada con éxito.
 *  - "error" + `fallo_accion` → acción EJECUTADA pero con fallo funcional
 *    (devolvió "Error: ...", patrón ADR-009 — p.ej. "Error: No encontré la app").
 *  - "error" + `fallo_ejecucion` → fallo estructural (excepción en la ejecución).
 *
 * La clasificación se aplica SOLO sobre el mensaje de la rama Success de
 * SystemCommandBridgeImpl; la rama Error estructural permanece verbatim con
 * `fallo_ejecucion` (contrato de tests existentes).
 */
data class ResultadoWire(val estado: String, val mensaje: String) {

    companion object {
        const val OK = "ok"
        const val ERROR = "error"

        /**
         * Clasifica el mensaje de una acción: "error" si empieza por el prefijo
         * ADR-009 EXACTO ("Error: " — mayúscula inicial, dos puntos y espacio;
         * case-sensitive a propósito: "error: ..." en minúsculas o "Error de ..."
         * sin dos puntos son contenido, no fallo), "ok" en cualquier otro caso.
         */
        fun estadoDe(mensaje: String): String =
            if (mensaje.startsWith("Error: ")) ERROR else OK
    }
}
