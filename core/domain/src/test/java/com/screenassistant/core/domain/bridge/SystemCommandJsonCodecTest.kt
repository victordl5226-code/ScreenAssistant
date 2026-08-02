package com.screenassistant.core.domain.bridge

import com.screenassistant.core.domain.bridge.SystemCommandJsonCodec.RespuestaCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cobertura del codec por fases (ADR-013, H3/H4): F0 (blank y límite de 8192
 * chars), F1 (versión/acción con precedencia E4>E5>E6>E7>E8), F2 (tipos vía
 * decodificador), Fase B' (semántica por campo: longitud primero, luego rango)
 * y encodeResult con explicitNulls=true.
 */
class SystemCommandJsonCodecTest {

    private val codec = SystemCommandJsonCodec()

    // ===== F0: entrada descartable sin parsear =====

    @Test
    fun `F0 blank es json_invalido`() {
        val error = codec.decode("") as RespuestaCodec.Error
        assertEquals("json_invalido", error.codigo)
        assertEquals("Error: JSON invalido.", error.mensaje)

        val errorEspacios = codec.decode("   ") as RespuestaCodec.Error
        assertEquals("json_invalido", errorEspacios.codigo)
    }

    private fun comandoDeLongitud(total: Int): String {
        // JSON válido (version:1 exigida por F1) con busqueda rellenable hasta el total.
        val base = """{"version":1,"accion":"buscar_google","busqueda":"""
        val padding = total - base.length - 3 // comilla inicial del valor + comilla final + cierre
        return base + "\"" + "a".repeat(padding) + "\"}"
    }

    @Test
    fun `F0 boundary 8192 chars pasa la longitud de comando`() {
        val input = comandoDeLongitud(8192)
        assertEquals(8192, input.length)
        // Pasa F0: el error (si lo hay) es de la Fase B', no de longitud de comando.
        val error = codec.decode(input) as RespuestaCodec.Error
        assertNotEquals("Error: Longitud excedida: comando (maximo 8192).", error.mensaje)
        assertEquals("longitud_excedida", error.codigo) // B': busqueda 8141 > 500
    }

    @Test
    fun `F0 boundary 8193 chars es longitud_excedida de comando`() {
        val input = comandoDeLongitud(8193)
        assertEquals(8193, input.length)
        val error = codec.decode(input) as RespuestaCodec.Error
        assertEquals("longitud_excedida", error.codigo)
        assertEquals("Error: Longitud excedida: comando (maximo 8192).", error.mensaje)
    }

    // ===== F1: versión y acción (E4>E5>E6>E7>E8) =====

    @Test
    fun `F1 version ausente es version_no_soportada`() {
        val error = codec.decode("""{"accion":"llamar_contacto","contacto":"Ana"}""") as RespuestaCodec.Error
        assertEquals("version_no_soportada", error.codigo)
        assertEquals("Error: Version no soportada.", error.mensaje)
    }

    @Test
    fun `F1 version string es version_no_soportada`() {
        val error = codec.decode("""{"accion":"llamar_contacto","contacto":"Ana","version":"1"}""") as RespuestaCodec.Error
        assertEquals("version_no_soportada", error.codigo)
    }

    @Test
    fun `F1 version 2 es version_no_soportada`() {
        val error = codec.decode("""{"accion":"llamar_contacto","contacto":"Ana","version":2}""") as RespuestaCodec.Error
        assertEquals("version_no_soportada", error.codigo)
    }

    @Test
    fun `F1 version 0 es version_no_soportada`() {
        val error = codec.decode("""{"accion":"llamar_contacto","contacto":"Ana","version":0}""") as RespuestaCodec.Error
        assertEquals("version_no_soportada", error.codigo)
    }

    @Test
    fun `F1 version decimal 1 0 es version_no_soportada`() {
        val error = codec.decode("""{"accion":"llamar_contacto","contacto":"Ana","version":1.0}""") as RespuestaCodec.Error
        assertEquals("version_no_soportada", error.codigo)
    }

    @Test
    fun `F1 version objeto es version_no_soportada`() {
        val error = codec.decode("""{"version":{},"accion":"llamar_contacto","contacto":"Ana"}""") as RespuestaCodec.Error
        assertEquals("version_no_soportada", error.codigo)
    }

    @Test
    fun `F1 version array es version_no_soportada`() {
        val error = codec.decode("""{"version":[],"accion":"llamar_contacto","contacto":"Ana"}""") as RespuestaCodec.Error
        assertEquals("version_no_soportada", error.codigo)
    }

    @Test
    fun `F1 raiz no objeto es version_no_soportada`() {
        val error = codec.decode("[1,2,3]") as RespuestaCodec.Error
        assertEquals("version_no_soportada", error.codigo)
        val errorTexto = codec.decode("\"hola\"") as RespuestaCodec.Error
        assertEquals("version_no_soportada", errorTexto.codigo)
    }

    @Test
    fun `F1 accion ausente es accion_desconocida con mensaje fijo`() {
        val error = codec.decode("""{"version":1,"contacto":"Ana"}""") as RespuestaCodec.Error
        assertEquals("accion_desconocida", error.codigo)
        assertEquals("Error: Accion desconocida.", error.mensaje)
    }

    @Test
    fun `F1 accion no string es accion_desconocida con mensaje fijo`() {
        val error = codec.decode("""{"version":1,"accion":5}""") as RespuestaCodec.Error
        assertEquals("accion_desconocida", error.codigo)
        assertEquals("Error: Accion desconocida.", error.mensaje)
    }

    @Test
    fun `F1 accion objeto es accion_desconocida con mensaje fijo`() {
        val error = codec.decode("""{"version":1,"accion":{"x":1}}""") as RespuestaCodec.Error
        assertEquals("accion_desconocida", error.codigo)
        assertEquals("Error: Accion desconocida.", error.mensaje)
    }

    @Test
    fun `F1 accion array es accion_desconocida con mensaje fijo`() {
        val error = codec.decode("""{"version":1,"accion":["x"]}""") as RespuestaCodec.Error
        assertEquals("accion_desconocida", error.codigo)
        assertEquals("Error: Accion desconocida.", error.mensaje)
    }

    @Test
    fun `F1 accion desconocida reporta el wire en el mensaje`() {
        val error = codec.decode("""{"version":1,"accion":"hacer_magia"}""") as RespuestaCodec.Error
        assertEquals("accion_desconocida", error.codigo)
        assertEquals("Error: Accion desconocida: 'hacer_magia'.", error.mensaje)
    }

    @Test
    fun `F1 accion en mayusculas es desconocida (case-sensitive)`() {
        val error = codec.decode("""{"version":1,"accion":"PONER_VOLUMEN"}""") as RespuestaCodec.Error
        assertEquals("accion_desconocida", error.codigo)
        assertTrue(error.mensaje.contains("'PONER_VOLUMEN'"))
    }

    @Test
    fun `F1 precedencia version mala gana a accion mala`() {
        val error = codec.decode("""{"accion":"hacer_magia","contacto":"Ana"}""") as RespuestaCodec.Error
        assertEquals("version_no_soportada", error.codigo)
    }

    @Test
    fun `F1 id string se propaga como eco en errores de version`() {
        val error = codec.decode("""{"version":9,"id":"t-99","accion":"llamar_contacto","contacto":"Ana"}""") as RespuestaCodec.Error
        assertEquals("version_no_soportada", error.codigo)
        assertEquals("t-99", error.idEco)
    }

    // ===== F2: tipos de los campos =====

    @Test
    fun `F2 tipo incorrecto hora string es json_invalido`() {
        val error = codec.decode("""{"version":1,"accion":"poner_alarma","hora":"siete","minuto":30}""") as RespuestaCodec.Error
        assertEquals("json_invalido", error.codigo)
        assertEquals("Error: JSON invalido.", error.mensaje)
    }

    @Test
    fun `F2 clave extra es json_invalido con ignoreUnknownKeys false`() {
        val error = codec.decode("""{"version":1,"accion":"llamar_contacto","contacto":"Ana","extra":1}""") as RespuestaCodec.Error
        assertEquals("json_invalido", error.codigo)
    }

    @Test
    fun `F2 campo requerido ausente es json_invalido`() {
        val error = codec.decode("""{"version":1,"accion":"llamar_contacto"}""") as RespuestaCodec.Error
        assertEquals("json_invalido", error.codigo)
    }

    @Test
    fun `F2 id numerico es json_invalido`() {
        val error = codec.decode("""{"version":1,"accion":"llamar_contacto","contacto":"Ana","id":5}""") as RespuestaCodec.Error
        assertEquals("json_invalido", error.codigo)
    }

    @Test
    fun `F2 json sintacticamente roto es json_invalido`() {
        val error = codec.decode("""{"version":1,"accion":"llamar_contacto" """) as RespuestaCodec.Error
        assertEquals("json_invalido", error.codigo)
    }

    @Test
    fun `F2 data object con clave extra es json_invalido`() {
        val error = codec.decode("""{"version":1,"accion":"abrir_alarmas","extra":1}""") as RespuestaCodec.Error
        assertEquals("json_invalido", error.codigo)
        assertEquals("Error: JSON invalido.", error.mensaje)
    }

    @Test
    fun `F2 data object con id numerico es json_invalido`() {
        val error = codec.decode("""{"version":1,"accion":"abrir_alarmas","id":5}""") as RespuestaCodec.Error
        assertEquals("json_invalido", error.codigo)
    }

    @Test
    fun `F2 data object con contexto numerico es json_invalido`() {
        val error = codec.decode("""{"version":1,"accion":"leer_notas","contexto":7}""") as RespuestaCodec.Error
        assertEquals("json_invalido", error.codigo)
    }

    // ===== Fase B': límites de la base (id/contexto ≤ 200, en CHARS) =====

    @Test
    fun `B1 id de 200 chars es valido`() {
        val id = "i".repeat(200)
        val r = codec.decode("""{"version":1,"accion":"poner_alarma","id":"$id","hora":7,"minuto":0}""")
        assertTrue(r is RespuestaCodec.Success)
    }

    @Test
    fun `B1 id de 201 chars es longitud_excedida id`() {
        val id = "i".repeat(201)
        val error = codec.decode("""{"version":1,"accion":"poner_alarma","id":"$id","hora":7,"minuto":0}""") as RespuestaCodec.Error
        assertEquals("longitud_excedida", error.codigo)
        assertEquals("Error: Longitud excedida: id (maximo 200).", error.mensaje)
    }

    @Test
    fun `B1 id de 201 chars en data object es longitud_excedida id`() {
        val id = "i".repeat(201)
        val error = codec.decode("""{"version":1,"accion":"abrir_alarmas","id":"$id"}""") as RespuestaCodec.Error
        assertEquals("longitud_excedida", error.codigo)
        assertEquals("Error: Longitud excedida: id (maximo 200).", error.mensaje)
    }

    @Test
    fun `B1 contexto de 200 chars es valido`() {
        val contexto = "c".repeat(200)
        val r = codec.decode("""{"version":1,"accion":"crear_nota","texto":"hola","contexto":"$contexto"}""")
        assertTrue(r is RespuestaCodec.Success)
    }

    @Test
    fun `B1 contexto de 201 chars es longitud_excedida contexto`() {
        val contexto = "c".repeat(201)
        val error = codec.decode(
            """{"version":1,"accion":"leer_notas","contexto":"$contexto"}"""
        ) as RespuestaCodec.Error
        assertEquals("longitud_excedida", error.codigo)
        assertEquals("Error: Longitud excedida: contexto (maximo 200).", error.mensaje)
    }

    @Test
    fun `B1 id excedido gana a campo invalido por orden de declaracion`() {
        val id = "i".repeat(201)
        val error = codec.decode("""{"version":1,"accion":"poner_alarma","id":"$id","hora":24,"minuto":0}""") as RespuestaCodec.Error
        assertEquals("longitud_excedida", error.codigo)
        assertEquals("Error: Longitud excedida: id (maximo 200).", error.mensaje)
    }

    // ===== Fase B': rangos de alarma =====

    @Test
    fun `B1 hora 0 es valida`() =
        assertEquals(
            RespuestaCodec.Success(com.screenassistant.core.domain.bridge.model.CommandEnvelope.PonerAlarma(0, 0)),
            codec.decode("""{"version":1,"accion":"poner_alarma","hora":0,"minuto":0}""")
        )

    @Test
    fun `B1 hora 23 es valida`() {
        val r = codec.decode("""{"version":1,"accion":"poner_alarma","hora":23,"minuto":59}""")
        assertTrue(r is RespuestaCodec.Success)
    }

    @Test
    fun `B1 hora 24 es valor_invalido hora`() {
        val error = codec.decode("""{"version":1,"accion":"poner_alarma","hora":24,"minuto":0}""") as RespuestaCodec.Error
        assertEquals("valor_invalido", error.codigo)
        assertEquals("Error: Valor invalido: hora (24).", error.mensaje)
    }

    @Test
    fun `B1 minuto 59 es valido`() {
        val r = codec.decode("""{"version":1,"accion":"poner_alarma","hora":7,"minuto":59}""")
        assertTrue(r is RespuestaCodec.Success)
    }

    @Test
    fun `B1 minuto 60 es valor_invalido minuto`() {
        val error = codec.decode("""{"version":1,"accion":"poner_alarma","hora":7,"minuto":60}""") as RespuestaCodec.Error
        assertEquals("valor_invalido", error.codigo)
        assertEquals("Error: Valor invalido: minuto (60).", error.mensaje)
    }

    // ===== Fase B': temporizador =====

    @Test
    fun `B1 temporizador 0 es valor_invalido`() {
        val error = codec.decode("""{"version":1,"accion":"poner_temporizador","minutos":0}""") as RespuestaCodec.Error
        assertEquals("valor_invalido", error.codigo)
        assertEquals("Error: Valor invalido: minutos (0).", error.mensaje)
    }

    @Test
    fun `B1 temporizador 1440 es valido`() {
        val r = codec.decode("""{"version":1,"accion":"poner_temporizador","minutos":1440}""")
        assertTrue(r is RespuestaCodec.Success)
    }

    @Test
    fun `B1 temporizador 1441 es valor_invalido`() {
        val error = codec.decode("""{"version":1,"accion":"poner_temporizador","minutos":1441}""") as RespuestaCodec.Error
        assertEquals("valor_invalido", error.codigo)
        assertEquals("Error: Valor invalido: minutos (1441).", error.mensaje)
    }

    // ===== Fase B': tablas (volumen, idioma, plataforma) =====

    @Test
    fun `B1 volumen desconocido es valor_invalido`() {
        val error = codec.decode("""{"version":1,"accion":"poner_volumen","valor":"maximoo"}""") as RespuestaCodec.Error
        assertEquals("valor_invalido", error.codigo)
        assertEquals("Error: Valor invalido: valor (maximoo).", error.mensaje)
    }

    @Test
    fun `B1 idioma desconocido es valor_invalido`() {
        val error = codec.decode("""{"version":1,"accion":"poner_idioma","idioma":"frances"}""") as RespuestaCodec.Error
        assertEquals("valor_invalido", error.codigo)
        assertEquals("Error: Valor invalido: idioma (frances).", error.mensaje)
    }

    @Test
    fun `B1 plataforma no soportada es valor_invalido`() {
        val error = codec.decode(
            """{"version":1,"accion":"encolar_mensaje","plataforma":"telegram","contacto":"Ana","mensaje":"Hola"}"""
        ) as RespuestaCodec.Error
        assertEquals("valor_invalido", error.codigo)
        assertEquals("Error: Valor invalido: plataforma (telegram).", error.mensaje)
    }

    // ===== Fase B': teléfono =====

    @Test
    fun `B1 telefono de 2 digitos es valor_invalido`() {
        val error = codec.decode("""{"version":1,"accion":"llamar_numero","telefono":"12"}""") as RespuestaCodec.Error
        assertEquals("valor_invalido", error.codigo)
        assertEquals("Error: Valor invalido: telefono (12).", error.mensaje)
    }

    @Test
    fun `B1 telefono con prefijo internacional es valido`() {
        val r = codec.decode("""{"version":1,"accion":"llamar_numero","telefono":"+34600123456"}""")
        assertTrue(r is RespuestaCodec.Success)
    }

    @Test
    fun `B1 telefono con letras es valor_invalido`() {
        val error = codec.decode("""{"version":1,"accion":"llamar_numero","telefono":"abc"}""") as RespuestaCodec.Error
        assertEquals("valor_invalido", error.codigo)
    }

    @Test
    fun `B1 telefono de 15 digitos es valido`() {
        val r = codec.decode("""{"version":1,"accion":"llamar_numero","telefono":"600123456789012"}""")
        assertTrue(r is RespuestaCodec.Success)
    }

    @Test
    fun `B1 telefono de 16 digitos es valor_invalido`() {
        val error = codec.decode("""{"version":1,"accion":"llamar_numero","telefono":"6001234567890123"}""") as RespuestaCodec.Error
        assertEquals("valor_invalido", error.codigo)
        assertEquals("Error: Valor invalido: telefono (6001234567890123).", error.mensaje)
    }

    @Test
    fun `B1 telefono con prefijo y 16 digitos excede la longitud`() {
        // '+' + 16 dígitos = 17 chars > 16 (máx. del registro: '+' + 15 dígitos).
        val error = codec.decode("""{"version":1,"accion":"llamar_numero","telefono":"+3460012345678901"}""") as RespuestaCodec.Error
        assertEquals("longitud_excedida", error.codigo)
        assertEquals("Error: Longitud excedida: telefono (maximo 16).", error.mensaje)
    }

    // ===== Fase B': blank en requeridos y longitudes límite =====

    @Test
    fun `B1 blank en campo requerido es valor_invalido`() {
        val error = codec.decode("""{"version":1,"accion":"llamar_contacto","contacto":"   "}""") as RespuestaCodec.Error
        assertEquals("valor_invalido", error.codigo)
        assertTrue(error.mensaje.startsWith("Error: Valor invalido: contacto"))
    }

    @Test
    fun `B1 blank en busqueda opcional de youtube es valor_invalido`() {
        val error = codec.decode("""{"version":1,"accion":"abrir_youtube","busqueda":" "}""") as RespuestaCodec.Error
        assertEquals("valor_invalido", error.codigo)
    }

    @Test
    fun `B1 texto de nota en el limite 1000 es valido`() {
        val texto = "a".repeat(1000)
        val r = codec.decode("""{"version":1,"accion":"crear_nota","texto":"$texto"}""")
        assertTrue(r is RespuestaCodec.Success)
    }

    @Test
    fun `B1 texto de nota 1001 es longitud_excedida`() {
        val texto = "a".repeat(1001)
        val error = codec.decode("""{"version":1,"accion":"crear_nota","texto":"$texto"}""") as RespuestaCodec.Error
        assertEquals("longitud_excedida", error.codigo)
        assertEquals("Error: Longitud excedida: texto (maximo 1000).", error.mensaje)
    }

    @Test
    fun `B1 busqueda google en el limite 500 es valida`() {
        val busqueda = "b".repeat(500)
        val r = codec.decode("""{"version":1,"accion":"buscar_google","busqueda":"$busqueda"}""")
        assertTrue(r is RespuestaCodec.Success)
    }

    @Test
    fun `B1 busqueda google 501 es longitud_excedida`() {
        val busqueda = "b".repeat(501)
        val error = codec.decode("""{"version":1,"accion":"buscar_google","busqueda":"$busqueda"}""") as RespuestaCodec.Error
        assertEquals("longitud_excedida", error.codigo)
        assertEquals("Error: Longitud excedida: busqueda (maximo 500).", error.mensaje)
    }

    @Test
    fun `B1 etiqueta de alarma 101 es longitud_excedida`() {
        val etiqueta = "e".repeat(101)
        val error = codec.decode("""{"version":1,"accion":"poner_alarma","hora":7,"minuto":0,"etiqueta":"$etiqueta"}""") as RespuestaCodec.Error
        assertEquals("longitud_excedida", error.codigo)
        assertEquals("Error: Longitud excedida: etiqueta (maximo 100).", error.mensaje)
    }

    @Test
    fun `B1 etiqueta blank se normaliza a null en el mapeo`() {
        val r = codec.decode("""{"version":1,"accion":"poner_alarma","hora":7,"minuto":0,"etiqueta":"   "}""")
        val success = r as RespuestaCodec.Success
        assertEquals(
            com.screenassistant.core.domain.model.SystemCommand.SetAlarm(7, 0, null),
            codec.mapToSystemCommand(success.envelope)
        )
    }

    // ===== Trims: campos requeridos con espacios alrededor se trimean en el mapeo =====

    private fun mapear(json: String): com.screenassistant.core.domain.model.SystemCommand {
        val success = codec.decode(json) as RespuestaCodec.Success
        return codec.mapToSystemCommand(success.envelope)
    }

    @Test
    fun `B1 contacto con espacios se trimea en el mapeo`() {
        assertEquals(
            com.screenassistant.core.domain.model.SystemCommand.Call("Ana"),
            mapear("""{"version":1,"accion":"llamar_contacto","contacto":"  Ana  "}""")
        )
    }

    @Test
    fun `B1 mensaje con espacios se trimea en el mapeo`() {
        assertEquals(
            com.screenassistant.core.domain.model.SystemCommand.SendSms("Ana", "Hola"),
            mapear("""{"version":1,"accion":"enviar_sms","contacto":"Ana","mensaje":"  Hola  "}""")
        )
    }

    @Test
    fun `B1 busqueda de google con espacios se trimea en el mapeo`() {
        assertEquals(
            com.screenassistant.core.domain.model.SystemCommand.SearchGoogle("gatos"),
            mapear("""{"version":1,"accion":"buscar_google","busqueda":"  gatos  "}""")
        )
    }

    @Test
    fun `B1 destino con espacios se trimea en el mapeo`() {
        assertEquals(
            com.screenassistant.core.domain.model.SystemCommand.Navigate("la oficina"),
            mapear("""{"version":1,"accion":"navegar_a","destino":"  la oficina  "}""")
        )
    }

    // ===== Fase B': CancelAlarm (4 casos) =====

    @Test
    fun `B1 cancelar_alarma solo con hora es invalido`() {
        val error = codec.decode("""{"version":1,"accion":"cancelar_alarma","hora":7}""") as RespuestaCodec.Error
        assertEquals("valor_invalido", error.codigo)
        assertEquals(
            "Error: Valor invalido: cancelar_alarma (hora y minuto juntos o ninguno).",
            error.mensaje
        )
    }

    @Test
    fun `B1 cancelar_alarma solo con minuto es invalido`() {
        val error = codec.decode("""{"version":1,"accion":"cancelar_alarma","minuto":30}""") as RespuestaCodec.Error
        assertEquals("valor_invalido", error.codigo)
    }

    @Test
    fun `B1 cancelar_alarma sin campos es valido`() {
        val r = codec.decode("""{"version":1,"accion":"cancelar_alarma"}""")
        assertTrue(r is RespuestaCodec.Success)
    }

    @Test
    fun `B1 cancelar_alarma con hora y minuto es valido`() {
        val r = codec.decode("""{"version":1,"accion":"cancelar_alarma","hora":7,"minuto":30}""")
        assertTrue(r is RespuestaCodec.Success)
    }

    // ===== Fase B': precedencia entre campos =====

    @Test
    fun `B1 hora invalida gana a etiqueta excedida por orden de declaracion`() {
        val etiqueta = "e".repeat(200)
        val error = codec.decode(
            """{"version":1,"accion":"poner_alarma","hora":24,"minuto":0,"etiqueta":"$etiqueta"}"""
        ) as RespuestaCodec.Error
        assertEquals("valor_invalido", error.codigo)
        assertEquals("Error: Valor invalido: hora (24).", error.mensaje)
    }

    // ===== encodeResult: esquema fijo de 6 claves con explicitNulls =====

    @Test
    fun `encodeResult exito emite el envelope exacto de 6 claves`() {
        val s = codec.encodeResult("ok", "t-42", "Éxito: Alarma configurada para las 7:30.", null, null)
        assertEquals(
            """{"version":1,"id":"t-42","estado":"ok","resultado":"Éxito: Alarma configurada para las 7:30.","error":null,"mensaje":null}""",
            s
        )
    }

    @Test
    fun `encodeResult error emite resultado null literal`() {
        val s = codec.encodeResult("error", null, null, "json_invalido", "Error: JSON invalido.")
        assertEquals(
            """{"version":1,"id":null,"estado":"error","resultado":null,"error":"json_invalido","mensaje":"Error: JSON invalido."}""",
            s
        )
    }

    @Test
    fun `encodeResult con id null y resultado null mantiene las 6 claves`() {
        val s = codec.encodeResult("error", null, null, "longitud_excedida", "Error: Longitud excedida: comando (maximo 8192).")
        assertTrue(s.contains("\"resultado\":null"))
        assertTrue(s.contains("\"id\":null"))
        assertTrue(s.contains("\"error\":\"longitud_excedida\""))
    }
}
