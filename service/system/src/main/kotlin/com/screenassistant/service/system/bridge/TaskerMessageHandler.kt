package com.screenassistant.service.system.bridge

/**
 * Recibe el wire ya extraído (no-null) y devuelve SIEMPRE el JSON de 6 claves
 * (contrato H4 de ADR-013), incluido el timeout. Nunca excepciones hacia el emisor.
 *
 * `origen` viaja best-effort (el receiver pasa `intent.getPackage()`) como seam
 * preparado para la allowlist de Fase 3 (D5). HONESTIDAD (cierre QA/Supervisor):
 * con la configuración SOPORTADA (Send Intent con el campo Package, broadcast
 * package-specific) `getPackage()` devuelve el TARGET — nuestra app —, NO el emisor
 * real; sin Package el broadcast no llega al receiver estático en API 26+. La
 * allowlist de Fase 3 deberá evaluar `PendingResult.getSentUid()`/`getSentPackage()`
 * (API 28+, guard minSdk 26), validado en dispositivo (ADR-014/T5). NO se valida en
 * v1 (QA #5 ACEPTADO). No colisiona con el campo `contexto` del wire (D6): es
 * vocabulario del transporte, no del protocolo.
 */
interface TaskerMessageHandler {
    suspend fun handle(extra: String, origen: String?): String
}
