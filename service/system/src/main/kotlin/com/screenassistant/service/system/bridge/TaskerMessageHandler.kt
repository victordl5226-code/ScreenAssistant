package com.screenassistant.service.system.bridge

/**
 * Recibe el wire ya extraído (no-null) y devuelve SIEMPRE el JSON de 6 claves
 * (contrato H4 de ADR-013), incluido el timeout y el rechazo de token.
 * Nunca excepciones hacia el emisor.
 *
 * v1.2 (F1, rediseño tras veto B1/H1): `token` = extra del transporte con el
 * secreto compartido (ADR-015 §2.2). La identidad del emisor
 * (getSentFromUid/getSentFromPackage) NO es obtenible sin opt-in del emisor
 * (H1, ADR-015 §0.2) → la autenticación usa material que el emisor SÍ controla:
 * el extra token del propio Intent. El seam vuelve al espíritu de ADR-014/T6
 * (un solo parámetro de comando + material de transporte por parámetro).
 */
interface TaskerMessageHandler {
    suspend fun handle(extra: String, token: String?): String
}
