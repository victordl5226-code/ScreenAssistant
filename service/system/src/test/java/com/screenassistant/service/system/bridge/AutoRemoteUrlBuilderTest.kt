package com.screenassistant.service.system.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AutoRemoteUrlBuilder (P3, ADR-015 — QA #4): construcción PURA de la URL del
 * endpoint sendmessage de AutoRemote. URLEncoder UTF-8 para key (con trim) y
 * mensaje (JSON de 6 claves con comillas/acentos/espacios). Key blank → null.
 */
class AutoRemoteUrlBuilderTest {

    @Test
    fun `construye la url con key y mensaje url-encodeados`() {
        val url = AutoRemoteUrlBuilder.construir("Mi Key", "Hola á")

        assertEquals(
            "https://autoremotejoaomgcd.appspot.com/sendmessage?key=Mi+Key&message=Hola+%C3%A1",
            url,
        )
    }

    @Test
    fun `el mensaje json con comillas acentos y espacios se encodea`() {
        val url = AutoRemoteUrlBuilder.construir("k", """{"id":"a b"}""")

        assertEquals(
            "https://autoremotejoaomgcd.appspot.com/sendmessage?key=k&message=%7B%22id%22%3A%22a+b%22%7D",
            url,
        )
    }

    @Test
    fun `caracteres especiales del mensaje se encodean`() {
        val url = AutoRemoteUrlBuilder.construir("k", "a b&c=d")

        assertEquals(
            "https://autoremotejoaomgcd.appspot.com/sendmessage?key=k&message=a+b%26c%3Dd",
            url,
        )
    }

    @Test
    fun `la key se recorta antes de encodear`() {
        val url = AutoRemoteUrlBuilder.construir("  Mi Key  ", "m")

        assertTrue(url!!.startsWith("https://autoremotejoaomgcd.appspot.com/sendmessage?key=Mi+Key&message=m"))
    }

    @Test
    fun `key blank devuelve null`() {
        assertNull(AutoRemoteUrlBuilder.construir("", "m"))
        assertNull(AutoRemoteUrlBuilder.construir("   ", "m"))
    }

    @Test
    fun `key presente con mensaje vacio no es null`() {
        val url = AutoRemoteUrlBuilder.construir("k", "")

        assertEquals(
            "https://autoremotejoaomgcd.appspot.com/sendmessage?key=k&message=",
            url,
        )
    }
}
