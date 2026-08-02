package com.screenassistant.core.domain.bridge.model

import com.screenassistant.core.domain.model.SystemCommand
import kotlin.reflect.KClass

/**
 * Registro ÚNICO de los 22 wires del puente Tasker (ADR-013, H1).
 *
 * Fuente de verdad de la biyección wire↔subtipo (emparejado EXACTO,
 * case-sensitive) y de TODOS los límites de cada acción: longitudes máximas
 * por campo, rangos numéricos, tablas de valores válidos, teléfono (constantes
 * 3/15 dígitos) y los límites de la base del envelope (id/contexto ≤ 200).
 * El codec (Fase B') consulta aquí sus límites — no hardcodea ninguno.
 *
 * Naming de wires (justificación H1): verbo en imperativo/neutro en español,
 * snake_case: `poner_alarma` (no `set_alarm` — el wire es nuestro vocabulario,
 * no el del sistema) y `navegar_a` con preposición (el destino es argumento,
 * "navegar" desnudo no es natural en español).
 */
object AccionRegistry {

    /** Límite de la base del envelope: id/contexto en CHARS (ADR-013, S7). */
    const val MAX_ID_CONTEXTO_CHARS: Int = 200

    /** Teléfono (llamar_numero): 3..15 dígitos con '+' opcional (ADR-013, S1). */
    const val TELEFONO_MIN_DIGITOS: Int = 3
    const val TELEFONO_MAX_DIGITOS: Int = 15

    /** Límites de validación semántica (Fase B' del codec). */
    data class Limites(
        val longitudMaxPorCampo: Map<String, Int> = emptyMap(),
        val rangoHora: IntRange? = null,
        val rangoMinuto: IntRange? = null,
        val rangoMinutos: IntRange? = null,
        val valoresValidos: Set<String> = emptySet(),
    ) {
        /** Rango declarado para un campo (hora/minuto/minutos); null si no aplica. */
        fun rangoDe(campo: String): IntRange? = when (campo) {
            "hora" -> rangoHora
            "minuto" -> rangoMinuto
            "minutos" -> rangoMinutos
            else -> null
        }
    }

    data class AccionRegistrada(
        val wire: String,
        val tipo: KClass<out CommandEnvelope>,
        val limites: Limites = Limites(),
    )

    // Límites compartidos con el modelo de voz (fuente única: SystemCommand.MAX_NOTE_CHARS).
    private const val MAX_CONTACTO = 200
    private const val MAX_BUSQUEDA_CORTA = 200
    private const val MAX_BUSQUEDA_LARGA = 500
    private const val MAX_MENSAJE = SystemCommand.MAX_NOTE_CHARS
    private const val MAX_ETIQUETA = 100
    private const val MAX_PLATAFORMA = 20
    private const val MAX_TEXTOLARGO = SystemCommand.MAX_NOTE_CHARS

    private val entradas: List<AccionRegistrada> = listOf(
        AccionRegistrada(
            "llamar_contacto", CommandEnvelope.LlamarContacto::class,
            Limites(longitudMaxPorCampo = mapOf("contacto" to MAX_CONTACTO)),
        ),
        AccionRegistrada(
            "enviar_sms", CommandEnvelope.EnviarSms::class,
            Limites(longitudMaxPorCampo = mapOf("contacto" to MAX_CONTACTO, "mensaje" to MAX_MENSAJE)),
        ),
        AccionRegistrada(
            "poner_alarma", CommandEnvelope.PonerAlarma::class,
            Limites(
                longitudMaxPorCampo = mapOf("etiqueta" to MAX_ETIQUETA),
                rangoHora = 0..23,
                rangoMinuto = 0..59,
            ),
        ),
        AccionRegistrada(
            "cancelar_alarma", CommandEnvelope.CancelarAlarma::class,
            Limites(rangoHora = 0..23, rangoMinuto = 0..59),
        ),
        AccionRegistrada(
            "abrir_app", CommandEnvelope.AbrirApp::class,
            Limites(longitudMaxPorCampo = mapOf("aplicacion" to MAX_BUSQUEDA_CORTA)),
        ),
        AccionRegistrada(
            "buscar_archivo", CommandEnvelope.BuscarArchivo::class,
            Limites(longitudMaxPorCampo = mapOf("busqueda" to MAX_BUSQUEDA_CORTA)),
        ),
        AccionRegistrada(
            "encolar_mensaje", CommandEnvelope.EncolarMensaje::class,
            Limites(
                longitudMaxPorCampo = mapOf(
                    "plataforma" to MAX_PLATAFORMA,
                    "contacto" to MAX_CONTACTO,
                    "mensaje" to MAX_MENSAJE,
                ),
                valoresValidos = setOf("whatsapp"),
            ),
        ),
        AccionRegistrada("abrir_alarmas", CommandEnvelope.AbrirAlarmas::class),
        AccionRegistrada(
            "buscar_google", CommandEnvelope.BuscarGoogle::class,
            Limites(longitudMaxPorCampo = mapOf("busqueda" to MAX_BUSQUEDA_LARGA)),
        ),
        AccionRegistrada(
            "abrir_youtube", CommandEnvelope.AbrirYouTube::class,
            Limites(longitudMaxPorCampo = mapOf("busqueda" to MAX_BUSQUEDA_LARGA)),
        ),
        AccionRegistrada("abrir_whatsapp", CommandEnvelope.AbrirWhatsApp::class),
        AccionRegistrada(
            "reproducir_musica", CommandEnvelope.ReproducirMusica::class,
            Limites(longitudMaxPorCampo = mapOf("busqueda" to MAX_BUSQUEDA_LARGA)),
        ),
        AccionRegistrada(
            "poner_volumen", CommandEnvelope.PonerVolumen::class,
            Limites(valoresValidos = setOf("subir", "bajar", "maximo", "minimo", "silencio")),
        ),
        AccionRegistrada(
            "poner_idioma", CommandEnvelope.PonerIdioma::class,
            Limites(valoresValidos = setOf("espanol", "ingles")),
        ),
        AccionRegistrada(
            "poner_temporizador", CommandEnvelope.PonerTemporizador::class,
            Limites(rangoMinutos = 1..1440),
        ),
        AccionRegistrada(
            "navegar_a", CommandEnvelope.NavegarA::class,
            Limites(longitudMaxPorCampo = mapOf("destino" to MAX_BUSQUEDA_CORTA)),
        ),
        AccionRegistrada("abrir_ajustes", CommandEnvelope.AbrirAjustes::class),
        AccionRegistrada(
            "llamar_numero", CommandEnvelope.LlamarNumero::class,
            Limites(longitudMaxPorCampo = mapOf("telefono" to 16)), // '+' + 15 dígitos
        ),
        AccionRegistrada(
            "recordar_dato", CommandEnvelope.RecordarDato::class,
            Limites(longitudMaxPorCampo = mapOf("dato" to MAX_TEXTOLARGO)),
        ),
        AccionRegistrada(
            "crear_nota", CommandEnvelope.CrearNota::class,
            Limites(longitudMaxPorCampo = mapOf("texto" to MAX_TEXTOLARGO)),
        ),
        AccionRegistrada("leer_notas", CommandEnvelope.LeerNotas::class),
        AccionRegistrada(
            "leer_nota", CommandEnvelope.LeerNota::class,
            Limites(longitudMaxPorCampo = mapOf("busqueda" to MAX_BUSQUEDA_CORTA)),
        ),
    )

    private val porWire: Map<String, AccionRegistrada> = entradas.associateBy { it.wire }

    /** Los 22 wires, en orden de declaración. */
    val todosLosWires: List<String> get() = entradas.map { it.wire }

    fun esRegistrado(wire: String): Boolean = porWire.containsKey(wire)

    /** Subtipo (clase) del envelope para un wire. Emparejado EXACTO, case-sensitive. */
    fun subtipoDe(wire: String): KClass<out CommandEnvelope>? = porWire[wire]?.tipo

    /** Wire para un subtipo de envelope. */
    fun wireDe(subtipo: KClass<out CommandEnvelope>): String? = entradas.firstOrNull { it.tipo == subtipo }?.wire

    /** Especificación completa de un wire registrado. */
    fun accionDe(wire: String): AccionRegistrada? = porWire[wire]

    /** Longitud máxima de un campo del wire (null si el campo no tiene tope). */
    fun longitudMaxima(wire: String, campo: String): Int? =
        porWire[wire]?.limites?.longitudMaxPorCampo?.get(campo)
}
