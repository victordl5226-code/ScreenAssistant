package com.screenassistant.core.domain.bridge

import com.screenassistant.core.domain.bridge.model.AccionRegistry
import com.screenassistant.core.domain.bridge.model.CommandEnvelope
import com.screenassistant.core.domain.model.AssistantLanguage
import com.screenassistant.core.domain.model.SystemCommand
import com.screenassistant.core.domain.model.VolumeAction
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull

/**
 * Codec JSON del puente Tasker (ADR-013, H2/H3/H4/S5).
 *
 * Dos pasadas estrictas: F1 (estructura: versión/acción con precedencia
 * E4>E5>E6>E7>E8) y F2 (tipos vía decodificador), seguidas de la Fase B'
 * (validación semántica por campo, en orden de declaración: primero longitud,
 * luego rango/valores). El codec NUNCA ejecuta: `decode`/`mapToSystemCommand`
 * son puros y la ejecución es responsabilidad exclusiva del puente.
 *
 * Fuente única de límites (ADR-013, S7): la Fase B' consulta TODOS sus límites
 * en AccionRegistry — longitudes por campo, rangos (hora/minuto/minutos),
 * tablas de valores, teléfono (constantes 3/15 + longitud 16) y la base del
 * envelope (id/contexto ≤ 200). Nada se hardcodea en el codec.
 *
 * Reglas de mensaje (ADR-009 + H4): "Error: " + detalle en `mensaje`; el esquema
 * de respuesta emite SIEMPRE las 6 claves con explicitNulls=true
 * ("resultado":null literal incluso en errores).
 */
class SystemCommandJsonCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = false
        explicitNulls = true
    }
) {

    // ===== Constantes del protocolo (S6: límite en CHARS, no bytes) =====
    companion object {
        const val MAX_COMANDO_CHARS: Int = 8192
        const val VERSION_PROTOCOLO: Int = 1
        const val MSG_JSON_INVALIDO = "Error: JSON invalido."
        const val MSG_LONGITUD_COMANDO = "Error: Longitud excedida: comando (maximo 8192)."
        const val MSG_VERSION = "Error: Version no soportada."
        const val MSG_ACCION_DESCONOCIDA = "Error: Accion desconocida."
        const val MSG_CANCELAR_ALARMA =
            "Error: Valor invalido: cancelar_alarma (hora y minuto juntos o ninguno)."
        const val CODIGO_FALLO_EJECUCION = "fallo_ejecucion"

        // 3..15 dígitos con '+' opcional: constantes declaradas en AccionRegistry
        // (fuente única, S7) y el regex se construye a partir de ellas.
        private val TELEFONO_REGEX =
            Regex("""^\+?\d{${AccionRegistry.TELEFONO_MIN_DIGITOS},${AccionRegistry.TELEFONO_MAX_DIGITOS}}$""")

        /**
         * Los 4 data objects (sin campos): wire → fábrica de la instancia única.
         * Sus claves válidas se limitan a las de la base + el discriminante (F2).
         */
        private val ES_OBJETO_SIN_CAMPOS: Map<String, () -> CommandEnvelope> = mapOf(
            "abrir_alarmas" to { CommandEnvelope.AbrirAlarmas },
            "abrir_whatsapp" to { CommandEnvelope.AbrirWhatsApp },
            "abrir_ajustes" to { CommandEnvelope.AbrirAjustes },
            "leer_notas" to { CommandEnvelope.LeerNotas },
        )
    }

    /** Resultado del decode: envelope válido o error estructurado con id eco. */
    sealed class RespuestaCodec {
        data class Success(val envelope: CommandEnvelope, val idEco: String? = null) : RespuestaCodec()
        data class Error(val codigo: String, val mensaje: String, val idEco: String?) : RespuestaCodec()
    }

    // ===== Decode: F0 → F1 → F2 → Fase B' (precedencia de errores E4>E5>E6>E7>E8) =====

    fun decode(input: String): RespuestaCodec {
        // F0: entradas descartables sin parsear.
        if (input.isBlank()) return RespuestaCodec.Error("json_invalido", MSG_JSON_INVALIDO, null)
        if (input.length > MAX_COMANDO_CHARS) {
            return RespuestaCodec.Error("longitud_excedida", MSG_LONGITUD_COMANDO, null)
        }

        // F1: estructura del documento (el parseo fallido es json_invalido; el resto, semántico).
        val elemento = try {
            json.parseToJsonElement(input)
        } catch (e: Exception) {
            // SerializationException y IllegalArgumentException (JSON sintácticamente roto).
            return RespuestaCodec.Error("json_invalido", MSG_JSON_INVALIDO, null)
        }
        val objeto = elemento as? JsonObject
            ?: return RespuestaCodec.Error("version_no_soportada", MSG_VERSION, null)

        // id best-effort: eco del wire solo si es string; si no, null.
        val idEco = (objeto["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content

        // La version debe ser un NÚMERO entero 1: los primitivos string se excluyen
        // explícitamente (intOrNull de kotlinx 1.7 parsea "1" como Int — S1: el wire
        // manda version en JSON string y NO debe aceptarse).
        val version = (objeto["version"] as? JsonPrimitive)
            ?.takeIf { !it.isString }
            ?.intOrNull
        if (version != VERSION_PROTOCOLO) {
            return RespuestaCodec.Error("version_no_soportada", MSG_VERSION, idEco)
        }

        val wire = (objeto["accion"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (wire == null) {
            return RespuestaCodec.Error("accion_desconocida", MSG_ACCION_DESCONOCIDA, idEco)
        }
        if (!AccionRegistry.esRegistrado(wire)) {
            return RespuestaCodec.Error(
                "accion_desconocida", "Error: Accion desconocida: '$wire'.", idEco
            )
        }

        // F2: tipos de los campos vía decodificador real (polimorfismo por "accion").
        // Los 4 data objects no declaran campos en su descriptor: el decodificador
        // generado rechaza las claves de la base (version/id/contexto) con
        // ignoreUnknownKeys=false → se decodifican a mano validando esas claves.
        val envelope: CommandEnvelope = run {
            val fabrica = ES_OBJETO_SIN_CAMPOS[wire]
            if (fabrica != null) {
                val extra = objeto.keys - setOf("accion", "version", "id", "contexto")
                if (extra.isNotEmpty()) {
                    return RespuestaCodec.Error("json_invalido", MSG_JSON_INVALIDO, idEco)
                }
                // Asimetría con las data classes: id/contexto deben ser String o
                // JSON null (los tipos no-String son json_invalido, igual que F2).
                if (!esStringOJsonNull(objeto["id"]) || !esStringOJsonNull(objeto["contexto"])) {
                    return RespuestaCodec.Error("json_invalido", MSG_JSON_INVALIDO, idEco)
                }
                fabrica()
            } else {
                try {
                    json.decodeFromJsonElement(CommandEnvelope.serializer(), elemento)
                } catch (e: SerializationException) {
                    return RespuestaCodec.Error("json_invalido", MSG_JSON_INVALIDO, idEco)
                }
            }
        }

        // Fase B': límites de la base del envelope (id/contexto ≤ 200) sobre el
        // wire — cubre también los 4 data objects (su envelope no conserva
        // id/contexto) y precede a la semántica por campo (orden de declaración).
        validarBase(objeto, idEco)?.let { return it }

        // Fase B': validación semántica post-decode (longitud primero, luego rango/valores).
        val errorSemantico = validarSemantica(envelope)
        if (errorSemantico != null) {
            return RespuestaCodec.Error(errorSemantico.codigo, errorSemantico.mensaje, envelope.id ?: idEco)
        }
        return RespuestaCodec.Success(envelope, idEco)
    }

    // ===== Mapeo (biyección envelope → SystemCommand; trims y tablas wire→enum) =====

    fun mapToSystemCommand(envelope: CommandEnvelope): SystemCommand = when (envelope) {
        is CommandEnvelope.LlamarContacto -> SystemCommand.Call(envelope.contacto.trim())
        is CommandEnvelope.EnviarSms ->
            SystemCommand.SendSms(envelope.contacto.trim(), envelope.mensaje.trim())
        is CommandEnvelope.PonerAlarma -> SystemCommand.SetAlarm(
            envelope.hora, envelope.minuto, etiquetaLimpia(envelope.etiqueta)
        )
        is CommandEnvelope.CancelarAlarma -> SystemCommand.CancelAlarm(envelope.hora, envelope.minuto)
        is CommandEnvelope.AbrirApp -> SystemCommand.OpenApp(envelope.aplicacion.trim())
        is CommandEnvelope.BuscarArchivo -> SystemCommand.SearchFile(envelope.busqueda.trim())
        is CommandEnvelope.EncolarMensaje -> SystemCommand.QueueMessage(
            envelope.plataforma.trim(), envelope.contacto.trim(), envelope.mensaje.trim()
        )
        is CommandEnvelope.AbrirAlarmas -> SystemCommand.OpenAlarms
        is CommandEnvelope.BuscarGoogle -> SystemCommand.SearchGoogle(envelope.busqueda.trim())
        is CommandEnvelope.AbrirYouTube -> SystemCommand.OpenYouTube(envelope.busqueda?.trim())
        is CommandEnvelope.AbrirWhatsApp -> SystemCommand.OpenWhatsApp
        is CommandEnvelope.ReproducirMusica -> SystemCommand.PlayMusic(envelope.busqueda?.trim())
        is CommandEnvelope.PonerVolumen -> SystemCommand.SetVolume(volumenDe(envelope.valor))
        is CommandEnvelope.PonerIdioma -> SystemCommand.SetLanguage(idiomaDe(envelope.idioma))
        is CommandEnvelope.PonerTemporizador -> SystemCommand.SetTimer(envelope.minutos)
        is CommandEnvelope.NavegarA -> SystemCommand.Navigate(envelope.destino.trim())
        is CommandEnvelope.AbrirAjustes -> SystemCommand.OpenSettings
        is CommandEnvelope.LlamarNumero -> SystemCommand.CallNumber(envelope.telefono.trim())
        is CommandEnvelope.RecordarDato -> SystemCommand.SaveMemory(envelope.dato.trim())
        is CommandEnvelope.CrearNota -> SystemCommand.CreateNote(envelope.texto.trim())
        is CommandEnvelope.LeerNotas -> SystemCommand.ReadNotes
        is CommandEnvelope.LeerNota -> SystemCommand.ReadNote(envelope.busqueda.trim())
    }

    // ===== Respuesta: esquema fijo de 6 claves (H4), explicitNulls=true =====

    /**
     * Emite SIEMPRE {"version":1,"id":...,"estado":...,"resultado":...,"error":...,"mensaje":...}
     * con "resultado":null literal en los errores (explicitNulls=true; orden de claves fijo).
     */
    fun encodeResult(estado: String, id: String?, resultado: String?, error: String?, mensaje: String?): String {
        // JsonObject.toString() emite JSON compacto preservando el orden de las 6 claves
        // y los nulls literales (JsonNull → "null"); sin extensiones de serialización.
        return JsonObject(
            buildMap {
                put("version", JsonPrimitive(VERSION_PROTOCOLO))
                put("id", id?.let { JsonPrimitive(it) } ?: JsonNull)
                put("estado", JsonPrimitive(estado))
                put("resultado", resultado?.let { JsonPrimitive(it) } ?: JsonNull)
                put("error", error?.let { JsonPrimitive(it) } ?: JsonNull)
                put("mensaje", mensaje?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        ).toString()
    }

    // ===== Fase B': validación semántica (orden de declaración de campos) =====

    private data class ErrorSemantico(val codigo: String, val mensaje: String)

    private fun validarSemantica(envelope: CommandEnvelope): ErrorSemantico? = when (envelope) {
        is CommandEnvelope.LlamarContacto -> validarTextoRequerido("llamar_contacto", "contacto", envelope.contacto)
        is CommandEnvelope.EnviarSms -> validarTextoRequerido("enviar_sms", "contacto", envelope.contacto)
            ?: validarTextoRequerido("enviar_sms", "mensaje", envelope.mensaje)
        is CommandEnvelope.PonerAlarma -> validarRango("poner_alarma", "hora", envelope.hora)
            ?: validarRango("poner_alarma", "minuto", envelope.minuto)
            ?: validarEtiqueta(envelope.etiqueta)
        is CommandEnvelope.CancelarAlarma -> validarCancelarAlarma(envelope)
        is CommandEnvelope.AbrirApp -> validarTextoRequerido("abrir_app", "aplicacion", envelope.aplicacion)
        is CommandEnvelope.BuscarArchivo -> validarTextoRequerido("buscar_archivo", "busqueda", envelope.busqueda)
        is CommandEnvelope.EncolarMensaje -> validarEncolarMensaje(envelope)
        is CommandEnvelope.AbrirAlarmas -> null
        is CommandEnvelope.BuscarGoogle -> validarTextoRequerido("buscar_google", "busqueda", envelope.busqueda)
        is CommandEnvelope.AbrirYouTube -> validarTextoOpcional("abrir_youtube", "busqueda", envelope.busqueda)
        is CommandEnvelope.AbrirWhatsApp -> null
        is CommandEnvelope.ReproducirMusica -> validarTextoOpcional("reproducir_musica", "busqueda", envelope.busqueda)
        is CommandEnvelope.PonerVolumen -> validarValorEnTabla("poner_volumen", "valor", envelope.valor)
        is CommandEnvelope.PonerIdioma -> validarValorEnTabla("poner_idioma", "idioma", envelope.idioma)
        is CommandEnvelope.PonerTemporizador -> validarRango("poner_temporizador", "minutos", envelope.minutos)
        is CommandEnvelope.NavegarA -> validarTextoRequerido("navegar_a", "destino", envelope.destino)
        is CommandEnvelope.AbrirAjustes -> null
        is CommandEnvelope.LlamarNumero -> validarTelefono(envelope)
        is CommandEnvelope.RecordarDato -> validarTextoRequerido("recordar_dato", "dato", envelope.dato)
        is CommandEnvelope.CrearNota -> validarTextoRequerido("crear_nota", "texto", envelope.texto)
        is CommandEnvelope.LeerNotas -> null
        is CommandEnvelope.LeerNota -> validarTextoRequerido("leer_nota", "busqueda", envelope.busqueda)
    }

    private fun validarTextoRequerido(wire: String, campo: String, valor: String): ErrorSemantico? {
        val maximo = AccionRegistry.longitudMaxima(wire, campo) ?: return null
        if (valor.length > maximo) return longitudExcedida(campo, maximo)
        if (valor.trim().isEmpty()) return valorInvalido(campo, valor)
        return null
    }

    private fun validarTextoOpcional(wire: String, campo: String, valor: String?): ErrorSemantico? {
        if (valor == null) return null
        val maximo = AccionRegistry.longitudMaxima(wire, campo) ?: return null
        if (valor.length > maximo) return longitudExcedida(campo, maximo)
        // A diferencia de la etiqueta de alarma, blank en opcional de búsqueda NO se
        // normaliza a null: es un valor inválido (B').
        if (valor.trim().isEmpty()) return valorInvalido(campo, valor)
        return null
    }

    private fun validarEtiqueta(etiqueta: String?): ErrorSemantico? {
        if (etiqueta == null) return null
        val maximo = AccionRegistry.longitudMaxima("poner_alarma", "etiqueta") ?: return null
        if (etiqueta.length > maximo) return longitudExcedida("etiqueta", maximo)
        return null // blank → null se resuelve en el mapeo (no es error).
    }

    private fun validarRango(wire: String, campo: String, valor: Int): ErrorSemantico? {
        // Rango consultado en el registro (fuente única, S7): el codec no hardcodea.
        val rango = AccionRegistry.accionDe(wire)?.limites?.rangoDe(campo) ?: return null
        return if (valor in rango) null else valorInvalido(campo, valor.toString())
    }

    private fun validarCancelarAlarma(envelope: CommandEnvelope.CancelarAlarma): ErrorSemantico? {
        val ambos = envelope.hora != null && envelope.minuto != null
        val ninguno = envelope.hora == null && envelope.minuto == null
        if (!ambos && !ninguno) {
            return ErrorSemantico("valor_invalido", MSG_CANCELAR_ALARMA)
        }
        envelope.hora?.let { return validarRango("cancelar_alarma", "hora", it) }
        return envelope.minuto?.let { validarRango("cancelar_alarma", "minuto", it) }
    }

    private fun validarEncolarMensaje(envelope: CommandEnvelope.EncolarMensaje): ErrorSemantico? {
        validarTextoRequerido("encolar_mensaje", "plataforma", envelope.plataforma)?.let { return it }
        val plataformas = AccionRegistry.accionDe("encolar_mensaje")?.limites?.valoresValidos.orEmpty()
        if (envelope.plataforma !in plataformas) return valorInvalido("plataforma", envelope.plataforma)
        validarTextoRequerido("encolar_mensaje", "contacto", envelope.contacto)?.let { return it }
        return validarTextoRequerido("encolar_mensaje", "mensaje", envelope.mensaje)
    }

    private fun validarValorEnTabla(wire: String, campo: String, valor: String): ErrorSemantico? {
        val validos = AccionRegistry.accionDe(wire)?.limites?.valoresValidos.orEmpty()
        return if (valor in validos) null else valorInvalido(campo, valor)
    }

    private fun validarTelefono(envelope: CommandEnvelope.LlamarNumero): ErrorSemantico? {
        val limpio = envelope.telefono.trim()
        // Longitud máxima consultada en el registro (16 = '+' + 15 dígitos, S7);
        // la longitud NO depende de la regex (3..15 dígitos, constantes del registro).
        val maximo = AccionRegistry.longitudMaxima("llamar_numero", "telefono") ?: return null
        if (limpio.length > maximo) return longitudExcedida("telefono", maximo)
        return if (TELEFONO_REGEX.matches(limpio)) null else valorInvalido("telefono", envelope.telefono)
    }

    /** Fase B': límites de la base del envelope (id/contexto ≤ 200, en CHARS). */
    private fun validarBase(objeto: JsonObject, idEco: String?): RespuestaCodec.Error? {
        val maximo = AccionRegistry.MAX_ID_CONTEXTO_CHARS
        for (campo in listOf("id", "contexto")) {
            val longitud = (objeto[campo] as? JsonPrimitive)?.takeIf { it.isString }?.content?.length
            if (longitud != null && longitud > maximo) {
                val error = longitudExcedida(campo, maximo)
                return RespuestaCodec.Error(error.codigo, error.mensaje, idEco)
            }
        }
        return null
    }

    /** Tipo de la base en el path manual: String o JSON null (nada más). */
    private fun esStringOJsonNull(elemento: JsonElement?): Boolean = when (elemento) {
        null -> true
        is JsonNull -> true
        is JsonPrimitive -> elemento.isString
        else -> false
    }

    private fun longitudExcedida(campo: String, maximo: Int) =
        ErrorSemantico("longitud_excedida", "Error: Longitud excedida: $campo (maximo $maximo).")

    private fun valorInvalido(campo: String, valor: String) =
        ErrorSemantico("valor_invalido", "Error: Valor invalido: $campo ($valor).")

    // ===== Tablas wire→enum (S3: VolumeAction/AssistantLanguage NO son @Serializable) =====

    private fun volumenDe(valor: String): VolumeAction = when (valor) {
        "subir" -> VolumeAction.UP
        "bajar" -> VolumeAction.DOWN
        "maximo" -> VolumeAction.MAX
        "minimo" -> VolumeAction.MIN
        "silencio" -> VolumeAction.MUTE
        // Inalcanzable por construcción: la Fase B' ya validó la tabla.
        else -> error("valor de volumen sin validar: $valor")
    }

    private fun idiomaDe(idioma: String): AssistantLanguage = when (idioma) {
        "espanol" -> AssistantLanguage.SPANISH
        "ingles" -> AssistantLanguage.ENGLISH
        // Inalcanzable por construcción: la Fase B' ya validó la tabla.
        else -> error("idioma sin validar: $idioma")
    }

    private fun etiquetaLimpia(etiqueta: String?): String? =
        etiqueta?.trim()?.takeIf { it.isNotEmpty() }
}
