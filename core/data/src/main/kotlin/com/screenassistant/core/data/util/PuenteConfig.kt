package com.screenassistant.core.data.util

/**
 * Configuración compartida del puente Tasker (Lote 7, ADR-015 v1.2): UNA fuente de
 * verdad para F1 (token compartido del canal), F2 (privacidad de respuesta) y F3
 * (URL callback de AutoRemote por flag explícito).
 *
 * Vive en core:data/util junto a [ApiKeyProvider] (corrección H3): el store
 * necesita `androidx.security:security-crypto`, que es `implementation` de
 * core:data y NO se propaga al compile de service:system.
 *
 * H1: [packageRespuesta] es el valor CRUDO de guardado — puede ser null (lo que
 * escribe la UI cuando el campo está vacío). La MATERIALIZACIÓN del default vive
 * en [PuenteConfigStore.cargar] (clave ausente/blank → [PACKAGE_RESPUESTA_DEFAULT]);
 * el consumidor (emitter) NUNCA ve null/blank en producción.
 *
 * v1.2 (rediseño tras veto B1/H1): se eliminan allowlistActivada,
 * paquetesPermitidos y normalizarPaquetes (código muerto: la identidad del emisor
 * es inobtenible sin opt-in — ADR-015 §2.6). F1 pasa a ser autenticación de canal
 * por extra `token` del transporte, validada en el handler (fail-closed).
 */
data class PuenteConfig(
    /** F2: paquete del receptor del broadcast de respuesta (CRUDO de guardado; el
     *  STORE materializa el default taskerm si ausente/blank — H1 intacto). */
    val packageRespuesta: String? = null,
    /** F3: key del dispositivo AutoRemote (secreto de control → prefs cifradas). */
    val autoRemoteKey: String = "",
    /** F1 v1.2: token compartido del canal (extra "token" del transporte). blank =
     *  canal abierto (compat modo actual, documentado "no protegido"); no-blank =
     *  fail-closed en el handler (`token_invalido` si el extra no coincide). */
    val tokenCompartido: String = "",
    /** F3 v1.2: flag explícito de URL callback (default OFF — cero llamadas URL
     *  sorpresa; la URL solo se envía si el flag está activo Y hay key). */
    val enviarRespuestaURL: Boolean = false,
) {
    companion object {
        /**
         * Default fail-closed de F2 (ADR-015/P3): el extra `respuesta` con el texto
         * de las notas deja de ser legible por CUALQUIER app. Se materializa en
         * [PuenteConfigStore.cargar] (H1); vive aquí y NO en TaskerBridgeContract
         * porque core:data no puede depender de service:system (H3).
         */
        const val PACKAGE_RESPUESTA_DEFAULT = "net.dinglisch.android.taskerm"
    }
}
