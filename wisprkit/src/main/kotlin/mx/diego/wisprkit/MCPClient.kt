package mx.diego.wisprkit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.CacheControl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

sealed class WisprException(message: String) : Exception(message) {
    class Http(val code: Int) : WisprException("El servidor respondió HTTP $code.")
    object Unauthorized : WisprException("Token rechazado por Wispr Money.") {
        private fun readResolve(): Any = Unauthorized
    }
    class Rpc(val code: Int, detail: String) : WisprException("Error del servidor MCP: $detail")
    class Tool(detail: String) : WisprException("Wispr Money devolvió un error: $detail")
    class BadResponse(detail: String) : WisprException("Respuesta inesperada: $detail")
}

/**
 * Cliente MCP (transporte Streamable HTTP, JSON-RPC 2.0) contra el endpoint `/mcp` de Wispr Money.
 *
 * Sin caché de ningún tipo: OkHttp sin `Cache` y cada request con `no-cache`/`no-store`.
 * Cada llamada a una herramienta es una consulta en vivo al servidor.
 */
class MCPClient(
    private val endpoint: String,
    private val token: String,
    private val http: OkHttpClient = uncachedClient(),
    private val clientName: String = "WisprAndroid",
) {
    private val mutex = Mutex()
    private var sessionId: String? = null
    private var initialized = false
    private var nextId = 1

    /** Llama una herramienta y devuelve el texto (JSON) de su resultado. */
    suspend fun callTool(name: String, arguments: JsonObject = JsonObject(emptyMap())): String =
        mutex.withLock {
            try {
                callToolOnce(name, arguments)
            } catch (e: WisprException.Http) {
                // La sesión MCP expiró en el servidor: se renegocia una vez.
                if (e.code != 404 || sessionId == null) throw e
                sessionId = null
                initialized = false
                callToolOnce(name, arguments)
            }
        }

    private suspend fun callToolOnce(name: String, arguments: JsonObject): String {
        ensureInitialized()
        val result = request("tools/call", buildJsonObject {
            put("name", name)
            put("arguments", arguments)
        }).jsonObject
        val text = result["content"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "text" }
            ?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
        if (result["isError"]?.jsonPrimitive?.booleanOrNull == true) throw WisprException.Tool(text)
        return text
    }

    private suspend fun ensureInitialized() {
        if (initialized) return
        request("initialize", buildJsonObject {
            put("protocolVersion", PROTOCOL_VERSION)
            put("capabilities", JsonObject(emptyMap()))
            put("clientInfo", buildJsonObject {
                put("name", clientName)
                put("version", "0.1.0")
            })
        })
        notify("notifications/initialized")
        initialized = true
    }

    private fun baseRequest(body: String): Request {
        val builder = Request.Builder()
            .url(endpoint)
            .post(body.toRequestBody(JSON_TYPE))
            .cacheControl(CacheControl.Builder().noCache().noStore().build())
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json, text/event-stream")
        if (initialized) builder.header("MCP-Protocol-Version", PROTOCOL_VERSION)
        sessionId?.let { builder.header("Mcp-Session-Id", it) }
        return builder.build()
    }

    private suspend fun notify(method: String) {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
        }.toString()
        execute(baseRequest(body)) { }
    }

    private suspend fun request(method: String, params: JsonObject): JsonElement {
        val id = nextId++
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }.toString()
        val reply = execute(baseRequest(body)) { response ->
            response.header("Mcp-Session-Id")?.let { sessionId = it }
            val text = response.body.string()
            if (response.header("Content-Type").orEmpty().contains("text/event-stream")) {
                parseSSE(text, id)
            } else {
                WisprJson.parseToJsonElement(text).jsonObject
            }
        }
        reply["error"]?.let { error ->
            val obj = error.jsonObject
            throw WisprException.Rpc(
                obj["code"]?.jsonPrimitive?.intOrNull ?: 0,
                obj["message"]?.jsonPrimitive?.contentOrNull ?: "",
            )
        }
        return reply["result"] ?: throw WisprException.BadResponse("sin result en $method")
    }

    private suspend fun <T> execute(request: Request, read: (Response) -> T): T = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            when (response.code) {
                in 200..299 -> read(response)
                401, 403 -> throw WisprException.Unauthorized
                else -> throw WisprException.Http(response.code)
            }
        }
    }

    companion object {
        const val PROTOCOL_VERSION = "2025-06-18"
        private val JSON_TYPE = "application/json".toMediaType()

        fun uncachedClient(): OkHttpClient = OkHttpClient.Builder()
            .cache(null)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

        /** Extrae de un stream SSE el mensaje JSON-RPC que responde a `id`. */
        fun parseSSE(text: String, id: Int): JsonObject {
            for (event in text.replace("\r\n", "\n").split("\n\n")) {
                val payload = event.split("\n")
                    .filter { it.startsWith("data:") }
                    .joinToString("\n") { it.removePrefix("data:").trimStart(' ') }
                if (payload.isEmpty()) continue
                val message = runCatching { WisprJson.parseToJsonElement(payload).jsonObject }.getOrNull() ?: continue
                if ((message["id"] as? JsonPrimitive)?.intOrNull == id) return message
            }
            throw WisprException.BadResponse("el stream SSE no trajo la respuesta $id")
        }
    }
}

/** JSON tolerante: Wispr puede añadir campos nuevos sin romper la app. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
val WisprJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    namingStrategy = kotlinx.serialization.json.JsonNamingStrategy.SnakeCase
}
