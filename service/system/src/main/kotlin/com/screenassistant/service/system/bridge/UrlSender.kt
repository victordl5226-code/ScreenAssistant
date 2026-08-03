package com.screenassistant.service.system.bridge

/**
 * Seam de red de F3 (ADR-015): inyectable para tests (orquestador puro).
 * Contrato: NO lanza — devuelve false ante cualquier fallo (el orquestador es
 * best-effort; la URL es canal adicional, nunca rompe el flujo).
 */
interface UrlSender {
    /** true si el servidor respondió HTTP 2xx; false en cualquier fallo (no lanza). */
    suspend fun enviar(url: String): Boolean
}
