package com.screenassistant.service.system.bridge

import com.screenassistant.core.domain.action.SystemAction
import com.screenassistant.core.domain.bridge.CommandBridge
import com.screenassistant.core.domain.bridge.CommandClassifier
import com.screenassistant.core.domain.bridge.ResultadoWire
import com.screenassistant.core.domain.bridge.SystemCommandJsonCodec
import com.screenassistant.core.domain.bridge.SystemCommandJsonCodec.Companion.CODIGO_FALLO_EJECUCION
import com.screenassistant.core.domain.bridge.SystemCommandJsonCodec.RespuestaCodec
import com.screenassistant.core.domain.bridge.model.CommandEnvelope
import com.screenassistant.core.domain.model.ActionResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación del puente Tasker (ADR-013, H1): decode → clasificar → ejecutar.
 *
 * Fase 1: el puente SIEMPRE ejecuta, sea CONSULTA o ACCION (la clasificación se
 * computa y queda lista para la respuesta diferencial de Fase 2). Los errores de
 * decode se devuelven estructurados (nunca excepciones hacia Tasker); los fallos
 * de ejecución usan el código `fallo_ejecucion` con el reason verbatim
 * (patrón del parser: "Error: " + reason — ADR-009).
 */
@Singleton
class SystemCommandBridgeImpl @Inject constructor(
    private val systemAction: SystemAction,
    private val codec: SystemCommandJsonCodec = SystemCommandJsonCodec(),
) : CommandBridge {

    override suspend fun handle(input: String): String {
        return when (val respuesta = codec.decode(input)) {
            is RespuestaCodec.Error ->
                codec.encodeResult("error", respuesta.idEco, null, respuesta.codigo, respuesta.mensaje)
            is RespuestaCodec.Success -> ejecutar(respuesta.envelope, respuesta.idEco)
        }
    }

    private suspend fun ejecutar(envelope: CommandEnvelope, idEco: String?): String {
        val command = codec.mapToSystemCommand(envelope)
        // Clasificación por efecto: preparada para Fase 2 (respuesta diferencial
        // CONSULTA/ACCION); en Fase 1 no altera la respuesta (diseño del Arquitecto).
        CommandClassifier.clasificar(command)

        // Eco del id: envelope.id para data classes; para los 4 data objects el
        // envelope no conserva el id del wire → fallback a idEco (contrato H4).
        val id = envelope.id ?: idEco

        return try {
            when (val result = systemAction.execute(command)) {
                is ActionResult.Success -> {
                    // B7 (ADR-B7): el wire distingue el fallo FUNCIONAL (la acción
                    // terminó pero devolvió "Error: ...", patrón ADR-009) del éxito
                    // real. La rama Error ESTRUCTURAL de abajo permanece verbatim
                    // con fallo_ejecucion (contrato de tests existentes).
                    val resultado = ResultadoWire(ResultadoWire.estadoDe(result.message), result.message)
                    if (resultado.estado == ResultadoWire.ERROR) {
                        codec.encodeResult(
                            resultado.estado, id, null, TaskerBridgeContract.CODIGO_FALLO_ACCION, resultado.mensaje
                        )
                    } else {
                        codec.encodeResult(resultado.estado, id, resultado.mensaje, null, null)
                    }
                }
                is ActionResult.Error ->
                    codec.encodeResult(
                        "error", id, null, CODIGO_FALLO_EJECUCION, "Error: ${result.reason}"
                    )
            }
        } catch (e: Exception) {
            codec.encodeResult(
                "error", id, null, CODIGO_FALLO_EJECUCION, "Error: ${e.message ?: "Error desconocido"}"
            )
        }
    }
}
