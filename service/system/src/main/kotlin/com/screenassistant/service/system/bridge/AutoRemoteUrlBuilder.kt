package com.screenassistant.service.system.bridge

import java.net.URLEncoder

/**
 * Construcción PURA de la URL callback de AutoRemote (F3, ADR-015; verificado en
 * joaoapps.com/autoremote/direct): endpoint HTTPS público, HTTP GET puro, sin SDK
 * de AutoRemote ni dependencias nuevas (java.net.URLEncoder es JVM puro).
 *
 * `null` si la key está blank (canal no configurado). Mensaje = JSON de 6 claves
 * del codec, url-encoded con URLEncoder UTF-8 (igual que la key, que se recorta).
 */
object AutoRemoteUrlBuilder {
    const val ENDPOINT = "https://autoremotejoaomgcd.appspot.com/sendmessage"

    fun construir(key: String, mensaje: String): String? {
        if (key.isBlank()) return null
        return "$ENDPOINT?key=${URLEncoder.encode(key.trim(), "UTF-8")}" +
            "&message=${URLEncoder.encode(mensaje, "UTF-8")}"
    }
}
