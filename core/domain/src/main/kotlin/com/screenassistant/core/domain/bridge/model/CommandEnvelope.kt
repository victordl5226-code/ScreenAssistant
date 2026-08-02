@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.screenassistant.core.domain.bridge.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Envelope JSON del puente Tasker (ADR-013, H1).
 *
 * Discriminante polimórfico `accion` (nunca colisiona con los campos: la única
 * excepción natural sería el wire de volumen, cuyo campo se llama `valor`, no
 * `accion`). Biyección estricta wire↔subtipo definida en [AccionRegistry].
 *
 * Contrato del wire (F1): `version` OBLIGATORIA y == 1; `id` y `contexto`
 * opcionales (mejor esfuerzo, se propagan a la respuesta como eco).
 */
@Serializable
@kotlinx.serialization.json.JsonClassDiscriminator("accion")
sealed class CommandEnvelope {
    abstract val version: Int
    abstract val id: String?
    abstract val contexto: String?

    @Serializable
    @SerialName("llamar_contacto")
    data class LlamarContacto(
        val contacto: String,
        override val version: Int = 1,
        override val id: String? = null,
        override val contexto: String? = null,
    ) : CommandEnvelope()

    @Serializable
    @SerialName("enviar_sms")
    data class EnviarSms(
        val contacto: String,
        val mensaje: String,
        override val version: Int = 1,
        override val id: String? = null,
        override val contexto: String? = null,
    ) : CommandEnvelope()

    @Serializable
    @SerialName("poner_alarma")
    data class PonerAlarma(
        val hora: Int,
        val minuto: Int,
        val etiqueta: String? = null,
        override val version: Int = 1,
        override val id: String? = null,
        override val contexto: String? = null,
    ) : CommandEnvelope()

    @Serializable
    @SerialName("cancelar_alarma")
    data class CancelarAlarma(
        val hora: Int? = null,
        val minuto: Int? = null,
        override val version: Int = 1,
        override val id: String? = null,
        override val contexto: String? = null,
    ) : CommandEnvelope()

    @Serializable
    @SerialName("abrir_app")
    data class AbrirApp(
        val aplicacion: String,
        override val version: Int = 1,
        override val id: String? = null,
        override val contexto: String? = null,
    ) : CommandEnvelope()

    @Serializable
    @SerialName("buscar_archivo")
    data class BuscarArchivo(
        val busqueda: String,
        override val version: Int = 1,
        override val id: String? = null,
        override val contexto: String? = null,
    ) : CommandEnvelope()

    @Serializable
    @SerialName("encolar_mensaje")
    data class EncolarMensaje(
        val plataforma: String,
        val contacto: String,
        val mensaje: String,
        override val version: Int = 1,
        override val id: String? = null,
        override val contexto: String? = null,
    ) : CommandEnvelope()

    @Serializable
    @SerialName("abrir_alarmas")
    data object AbrirAlarmas : CommandEnvelope() {
        override val version: Int = 1
        override val id: String? = null
        override val contexto: String? = null
    }

    @Serializable
    @SerialName("buscar_google")
    data class BuscarGoogle(
        val busqueda: String,
        override val version: Int = 1,
        override val id: String? = null,
        override val contexto: String? = null,
    ) : CommandEnvelope()

    @Serializable
    @SerialName("abrir_youtube")
    data class AbrirYouTube(
        val busqueda: String? = null,
        override val version: Int = 1,
        override val id: String? = null,
        override val contexto: String? = null,
    ) : CommandEnvelope()

    @Serializable
    @SerialName("abrir_whatsapp")
    data object AbrirWhatsApp : CommandEnvelope() {
        override val version: Int = 1
        override val id: String? = null
        override val contexto: String? = null
    }

    @Serializable
    @SerialName("reproducir_musica")
    data class ReproducirMusica(
        val busqueda: String? = null,
        override val version: Int = 1,
        override val id: String? = null,
        override val contexto: String? = null,
    ) : CommandEnvelope()

    @Serializable
    @SerialName("poner_volumen")
    data class PonerVolumen(
        // "valor" (no "accion") para no colisionar con el discriminante.
        val valor: String,
        override val version: Int = 1,
        override val id: String? = null,
        override val contexto: String? = null,
    ) : CommandEnvelope()

    @Serializable
    @SerialName("poner_idioma")
    data class PonerIdioma(
        val idioma: String,
        override val version: Int = 1,
        override val id: String? = null,
        override val contexto: String? = null,
    ) : CommandEnvelope()

    @Serializable
    @SerialName("poner_temporizador")
    data class PonerTemporizador(
        val minutos: Int,
        override val version: Int = 1,
        override val id: String? = null,
        override val contexto: String? = null,
    ) : CommandEnvelope()

    @Serializable
    @SerialName("navegar_a")
    data class NavegarA(
        val destino: String,
        override val version: Int = 1,
        override val id: String? = null,
        override val contexto: String? = null,
    ) : CommandEnvelope()

    @Serializable
    @SerialName("abrir_ajustes")
    data object AbrirAjustes : CommandEnvelope() {
        override val version: Int = 1
        override val id: String? = null
        override val contexto: String? = null
    }

    @Serializable
    @SerialName("llamar_numero")
    data class LlamarNumero(
        val telefono: String,
        override val version: Int = 1,
        override val id: String? = null,
        override val contexto: String? = null,
    ) : CommandEnvelope()

    @Serializable
    @SerialName("recordar_dato")
    data class RecordarDato(
        val dato: String,
        override val version: Int = 1,
        override val id: String? = null,
        override val contexto: String? = null,
    ) : CommandEnvelope()

    @Serializable
    @SerialName("crear_nota")
    data class CrearNota(
        val texto: String,
        override val version: Int = 1,
        override val id: String? = null,
        override val contexto: String? = null,
    ) : CommandEnvelope()

    @Serializable
    @SerialName("leer_notas")
    data object LeerNotas : CommandEnvelope() {
        override val version: Int = 1
        override val id: String? = null
        override val contexto: String? = null
    }

    @Serializable
    @SerialName("leer_nota")
    data class LeerNota(
        val busqueda: String,
        override val version: Int = 1,
        override val id: String? = null,
        override val contexto: String? = null,
    ) : CommandEnvelope()
}
