package com.screenassistant.core.iot.data.repository

import android.util.Log
import com.screenassistant.core.iot.domain.model.smartHome.AutomationRule
import com.screenassistant.core.iot.domain.model.smartHome.HAServiceCall
import com.screenassistant.core.iot.domain.model.smartHome.HomeAssistantEntity
import com.screenassistant.core.iot.domain.repository.HomeAssistantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Implementación de [HomeAssistantRepository] usando la API REST y WebSocket de Home Assistant.
 *
 * ## API REST
 * - Base URL: `http://{host}:{port}/api/`
 * - Auth: Bearer token en header `Authorization`
 * - Docs: https://developers.home-assistant.io/docs/api/rest/
 *
 * ## WebSocket
 * - URL: `ws://{host}:{port}/api/websocket`
 * - Auth: `auth_required` → `auth_ok` handshake
 * - Events: `state_changed` para actualizaciones en tiempo real
 * - Docs: https://developers.home-assistant.io/docs/api/websocket/
 *
 * ## Configuración
 * El repositorio se configura con [configure] llamando con la URL base y el token.
 * Hasta que no se configure, todos los métodos retornan valores vacíos/false.
 *
 * ## Caché
 * - Entidades: MutableStateFlow con snapshot actualizado vía WebSocket
 * - Automatizaciones: MutableStateFlow actualizado vía REST polling
 * - Eventos: MutableSharedFlow para suscripciones en tiempo real
 */
@Singleton
class HomeAssistantRepositoryImpl @Inject constructor() : HomeAssistantRepository {

    companion object {
        private const val TAG = "HomeAssistantRepo"
        private const val CONNECT_TIMEOUT_SEC = 10L
        private const val READ_TIMEOUT_SEC = 30L
        private const val WRITE_TIMEOUT_SEC = 30L
    }

    // ── Configuración ──────────────────────────────────────────────────────

    @Volatile
    private var baseUrl: String? = null

    @Volatile
    private var token: String? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()

    // ── Estado en caché ────────────────────────────────────────────────────

    private val _entities = MutableStateFlow<List<HomeAssistantEntity>>(emptyList())
    private val _automations = MutableStateFlow<List<AutomationRule>>(emptyList())
    private val _events = MutableSharedFlow<Map<String, Any>>(extraBufferCapacity = 64)

    private var webSocket: WebSocket? = null

    /**
     * Configura la conexión con Home Assistant.
     *
     * @param url URL base (ej: "http://192.168.1.100:8123")
     * @param longLivedToken Token de acceso de larga duración
     */
    fun configure(url: String, longLivedToken: String) {
        val cleanUrl = url.trimEnd('/')
        baseUrl = cleanUrl
        token = longLivedToken
        Log.i(TAG, "Configured: $cleanUrl")
    }

    /** Indica si el repositorio está configurado. */
    val isConfigured: Boolean
        get() = baseUrl != null && token != null

    // ── REST helpers ───────────────────────────────────────────────────────

    private fun apiRequest(path: String): Request? {
        val url = baseUrl ?: return null
        val tok = token ?: return null
        return Request.Builder()
            .url("$url/api$path")
            .addHeader("Authorization", "Bearer $tok")
            .addHeader("Content-Type", "application/json")
            .build()
    }

    private fun apiGet(path: String): String? {
        val request = apiRequest(path) ?: return null
        return try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                Log.w(TAG, "GET $path → ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "GET $path failed", e)
            null
        }
    }

    private fun apiPost(path: String, body: String): String? {
        val request = apiRequest(path)?.newBuilder()
            ?.post(body.toRequestBody("application/json".toMediaType()))
            ?.build() ?: return null
        return try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                Log.w(TAG, "POST $path → ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "POST $path failed", e)
            null
        }
    }

    private fun apiPostRaw(path: String, body: String): Response? {
        val request = apiRequest(path)?.newBuilder()
            ?.post(body.toRequestBody("application/json".toMediaType()))
            ?.build() ?: return null
        return try {
            httpClient.newCall(request).execute()
        } catch (e: Exception) {
            Log.e(TAG, "POST $path failed", e)
            null
        }
    }

    // ── Entity mapping ─────────────────────────────────────────────────────

    private fun parseEntity(json: JsonObject): HomeAssistantEntity {
        val attributes = mutableMapOf<String, String>()
        json["attributes"]?.jsonObject?.forEach { (key, value) ->
            attributes[key] = when {
                value.jsonPrimitive.contentOrNull != null -> value.jsonPrimitive.content
                value.jsonPrimitive.boolean -> value.jsonPrimitive.boolean.toString()
                value.jsonPrimitive.int != null -> value.jsonPrimitive.int.toString()
                value.jsonPrimitive.double != null -> value.jsonPrimitive.double.toString()
                else -> value.toString()
            }
        }

        return HomeAssistantEntity(
            entityId = json["entity_id"]?.jsonPrimitive?.content ?: "",
            state = json["state"]?.jsonPrimitive?.content ?: "",
            attributes = attributes,
            lastChanged = kotlinx.datetime.Instant.parse(
                json["last_changed"]?.jsonPrimitive?.content ?: "2000-01-01T00:00:00Z"
            ),
            lastUpdated = kotlinx.datetime.Instant.parse(
                json["last_updated"]?.jsonPrimitive?.content ?: "2000-01-01T00:00:00Z"
            ),
            context = com.screenassistant.core.iot.domain.model.smartHome.HAContext(
                id = json["context"]?.jsonObject?.get("id")?.jsonPrimitive?.content ?: "",
                parentId = json["context"]?.jsonObject?.get("parent_id")?.jsonPrimitive?.contentOrNull,
                userId = json["context"]?.jsonObject?.get("user_id")?.jsonPrimitive?.contentOrNull,
            ),
        )
    }

    // ── HomeAssistantRepository implementation ──────────────────────────────

    override fun observeEntities(): Flow<List<HomeAssistantEntity>> = _entities.asStateFlow()

    override suspend fun getEntity(entityId: String): HomeAssistantEntity? {
        return withContext(Dispatchers.IO) {
            val response = apiGet("/states/$entityId") ?: return@withContext null
            try {
                parseEntity(json.parseToJsonElement(response).jsonObject)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing entity $entityId", e)
                null
            }
        }
    }

    override fun observeEntitiesByDomain(domain: String): Flow<List<HomeAssistantEntity>> {
        return _entities.map { entities ->
            entities.filter { it.domain.equals(domain, ignoreCase = true) }
        }
    }

    override suspend fun callService(call: HAServiceCall): Boolean {
        return withContext(Dispatchers.IO) {
            val body = buildMap {
                call.target?.entityIds?.takeIf { it.isNotEmpty() }?.let {
                    put("entity_id", it.joinToString(","))
                }
                call.data.forEach { (k, v) -> put(k, v) }
            }
            val bodyJson = json.encodeToString(
                kotlinx.serialization.serializer<Map<String, String>>(),
                body
            )
            val response = apiPost("/services/${call.domain}/${call.service}", bodyJson)
            response != null
        }
    }

    override suspend fun callServices(calls: List<HAServiceCall>): List<Boolean> {
        return calls.map { callService(it) }
    }

    override suspend fun checkConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            val response = apiGet("/")
            response != null && response.contains("message")
        }
    }

    override suspend fun getConfig(): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            val response = apiGet("/config") ?: return@withContext emptyMap()
            try {
                val jsonObj = json.parseToJsonElement(response).jsonObject
                buildMap {
                    jsonObj["components"]?.let { put("components", it.toString()) }
                    jsonObj["config_dir"]?.let { put("config_dir", it.toString()) }
                    jsonObj["latitude"]?.let { put("latitude", it.toString()) }
                    jsonObj["longitude"]?.let { put("longitude", it.toString()) }
                    jsonObj["time_zone"]?.let { put("time_zone", it.toString()) }
                    jsonObj["version"]?.let { put("version", it.toString()) }
                    jsonObj["unit_system"]?.let { put("unit_system", it.toString()) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing config", e)
                emptyMap()
            }
        }
    }

    // ── Automations ────────────────────────────────────────────────────────

    override fun observeAutomations(): Flow<List<AutomationRule>> = _automations.asStateFlow()

    override suspend fun getAutomation(automationId: String): AutomationRule? {
        return withContext(Dispatchers.IO) {
            val response = apiGet("/states/$automationId") ?: return@withContext null
            try {
                val entity = parseEntity(json.parseToJsonElement(response).jsonObject)
                mapEntityToAutomation(entity)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing automation $automationId", e)
                null
            }
        }
    }

    override suspend fun createAutomation(rule: AutomationRule): Boolean {
        // HA REST API no soporta creación directa de automatizaciones.
        // Se delega a la configuración YAML o se usa la WebSocket API.
        Log.w(TAG, "createAutomation not supported via REST API — use HA UI or YAML")
        return false
    }

    override suspend fun updateAutomation(rule: AutomationRule): Boolean {
        Log.w(TAG, "updateAutomation not supported via REST API — use HA UI or YAML")
        return false
    }

    override suspend fun deleteAutomation(automationId: String): Boolean {
        Log.w(TAG, "deleteAutomation not supported via REST API — use HA UI or YAML")
        return false
    }

    override suspend fun setAutomationEnabled(automationId: String, enabled: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            val service = if (enabled) "turn_on" else "turn_off"
            val body = """{"entity_id":"$automationId"}"""
            val response = apiPost("/services/automation/$service", body)
            response != null
        }
    }

    override suspend fun triggerAutomation(automationId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val body = """{"entity_id":"$automationId"}"""
            val response = apiPost("/services/automation/trigger", body)
            response != null
        }
    }

    override suspend fun getAutomationTrace(automationId: String): String? {
        return withContext(Dispatchers.IO) {
            apiGet("/trace/$automationId")
        }
    }

    // ── States & History ───────────────────────────────────────────────────

    override suspend fun getEntityHistory(
        entityId: String,
        startTime: Long,
        endTime: Long?,
    ): List<HomeAssistantEntity> {
        return withContext(Dispatchers.IO) {
            val startStr = java.time.Instant.ofEpochMilli(startTime).toString()
            val endParam = endTime?.let {
                "&end_time=${java.time.Instant.ofEpochMilli(it)}"
            } ?: ""
            val response = apiGet("/history/period/$startStr?filter_entity_id=$entityId$endParam")
                ?: return@withContext emptyList()
            try {
                val arr = json.parseToJsonElement(response).jsonArray
                if (arr.isNotEmpty()) {
                    arr[0].jsonArray.map { parseEntity(it.jsonObject) }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing history", e)
                emptyList()
            }
        }
    }

    override suspend fun getStates(entityIds: List<String>): Map<String, HomeAssistantEntity> {
        return withContext(Dispatchers.IO) {
            val response = apiGet("/states") ?: return@withContext emptyMap()
            try {
                val arr = json.parseToJsonElement(response).jsonArray
                arr.mapNotNull { jsonEl ->
                    val entity = parseEntity(jsonEl.jsonObject)
                    if (entityIds.isEmpty() || entity.entityId in entityIds) {
                        entity.entityId to entity
                    } else null
                }.toMap()
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing states", e)
                emptyMap()
            }
        }
    }

    // ── Events ─────────────────────────────────────────────────────────────

    override fun subscribeToEvents(eventType: String): Flow<Map<String, Any>> {
        return _events.map { event ->
            if (eventType == "*" || event["event_type"] == eventType) {
                event
            } else {
                emptyMap()
            }
        }
    }

    override suspend fun fireEvent(eventType: String, eventData: Map<String, Any>?): Boolean {
        return withContext(Dispatchers.IO) {
            val body = buildMap {
                eventData?.forEach { (k, v) -> put(k, v.toString()) }
            }
            val bodyJson = json.encodeToString(
                kotlinx.serialization.serializer<Map<String, String>>(),
                body
            )
            val response = apiPost("/events/$eventType", bodyJson)
            response != null
        }
    }

    // ── WebSocket ──────────────────────────────────────────────────────────

    /**
     * Conecta al WebSocket de Home Assistant para actualizaciones en tiempo real.
     * Se llama automáticamente al observar entidades por primera vez.
     */
    fun connectWebSocket() {
        val url = baseUrl ?: return
        val tok = token ?: return

        val wsUrl = url.replace("http://", "ws://").replace("https://", "wss://") +
            "/api/websocket"

        val request = Request.Builder().url(wsUrl).build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected")
                // Enviar auth
                webSocket.send("""{"type":"auth","access_token":"$tok"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = json.parseToJsonElement(text).jsonObject
                    val type = msg["type"]?.jsonPrimitive?.content

                    when (type) {
                        "auth_ok" -> {
                            Log.i(TAG, "WebSocket auth OK")
                            // Suscribir a state_changed
                            webSocket.send("""{"id":1,"type":"subscribe_events","event_type":"state_changed"}""")
                        }
                        "auth_invalid" -> {
                            Log.e(TAG, "WebSocket auth invalid")
                        }
                        "event" -> {
                            val eventData = msg["event"]?.jsonObject
                            val eventType = eventData?.get("event_type")?.jsonPrimitive?.content

                            if (eventType == "state_changed") {
                                val entityData = eventData["data"]?.jsonObject?.get("new_state")?.jsonObject
                                if (entityData != null) {
                                    val entity = parseEntity(entityData)
                                    _entities.update { current ->
                                        current.map { if (it.entityId == entity.entityId) entity else it }
                                            .let { updated ->
                                                if (updated.any { it.entityId == entity.entityId }) updated
                                                else updated + entity
                                            }
                                    }
                                }
                            }

                            // Emitir evento crudo
                            val eventMap = mutableMapOf<String, Any>()
                            msg.forEach { (k, v) -> eventMap[k] = v.toString() }
                            _events.tryEmit(eventMap)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing WebSocket message", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
            }
        })
    }

    /**
     * Desconecta el WebSocket.
     */
    fun disconnectWebSocket() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }

    /**
     * Carga el estado inicial de todas las entidades desde REST.
     * Se llama para inicializar el caché antes de conectar el WebSocket.
     */
    suspend fun refreshEntities() {
        withContext(Dispatchers.IO) {
            val response = apiGet("/states") ?: return@withContext
            try {
                val arr = json.parseToJsonElement(response).jsonArray
                val entities = arr.map { parseEntity(it.jsonObject) }
                _entities.value = entities
                Log.i(TAG, "Refreshed ${entities.size} entities")
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing entities", e)
            }
        }
    }

    /**
     * Carga las automatizaciones desde REST.
     */
    suspend fun refreshAutomations() {
        withContext(Dispatchers.IO) {
            val response = apiGet("/states") ?: return@withContext
            try {
                val arr = json.parseToJsonElement(response).jsonArray
                val automations = arr.mapNotNull { jsonEl ->
                    val entity = parseEntity(jsonEl.jsonObject)
                    if (entity.domain == "automation") mapEntityToAutomation(entity) else null
                }
                _automations.value = automations
                Log.i(TAG, "Refreshed ${automations.size} automations")
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing automations", e)
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun mapEntityToAutomation(entity: HomeAssistantEntity): AutomationRule {
        return AutomationRule(
            ruleId = entity.entityId,
            name = entity.friendlyName,
            description = entity.getStringAttribute("id") ?: "",
            enabled = entity.isOn,
            triggers = emptyList(), // HA no expone triggers via REST
            conditions = emptyList(),
            actions = emptyList(),
        )
    }

    /** Limpia recursos. */
    fun destroy() {
        disconnectWebSocket()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }
}
