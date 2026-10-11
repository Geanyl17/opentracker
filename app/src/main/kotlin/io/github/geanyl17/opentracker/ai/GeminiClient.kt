package io.github.geanyl17.opentracker.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GeminiException(message: String, val status: Int = 0) : Exception(message)

/** A small client for the Gemini Interactions API, which serves both Gemini models and the Antigravity agent. */
class GeminiClient(private val apiKey: () -> String?) {
    suspend fun create(body: JsonObject): JsonObject = call("POST", "interactions", body)

    suspend fun get(id: String): JsonObject = call("GET", "interactions/$id")

    suspend fun cancel(id: String) {
        call("POST", "interactions/$id:cancel", JsonObject(emptyMap()))
    }

    suspend fun delete(id: String) {
        call("DELETE", "interactions/$id")
    }

    private suspend fun call(method: String, path: String, body: JsonObject? = null): JsonObject = withContext(Dispatchers.IO) {
        val key = apiKey()?.takeIf { it.isNotBlank() }
            ?: throw GeminiException("Add your Gemini API key in Settings first.")
        val connection = URL("$BASE_URL/$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 120_000
            connection.setRequestProperty("x-goog-api-key", key)
            connection.setRequestProperty("Api-Revision", API_REVISION)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().encodeToByteArray()) }
            }
            val status = connection.responseCode
            val text = (if (status < 400) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status >= 400) throw GeminiException(describe(status, text), status)
            if (text.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(text).jsonObject
        } catch (e: IOException) {
            throw GeminiException("Couldn't reach Google. Check your connection and try again.")
        } catch (e: SerializationException) {
            throw GeminiException("Google sent a response the app couldn't read.")
        } catch (e: IllegalArgumentException) {
            throw GeminiException("Google sent a response the app couldn't read.")
        } finally {
            connection.disconnect()
        }
    }

    private fun describe(status: Int, body: String): String {
        val message = errorMessage(body)?.take(300)
        return when {
            message != null && message.contains("API key", ignoreCase = true) ->
                "Google rejected the API key. Check it in Settings."
            status == 401 || status == 403 -> message ?: "Google refused the request. Check your API key in Settings."
            status == 404 -> "Google couldn't find that model or agent. Check its name in Settings."
            status == 429 -> "You've hit Gemini's rate limit. Wait a minute and try again."
            status >= 500 -> "Google's servers had a problem. Try again in a moment."
            else -> message ?: "The request failed (error $status)."
        }
    }

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val API_REVISION = "2026-05-20"
        val json = Json { ignoreUnknownKeys = true }
    }
}

data class FunctionCall(val id: String, val name: String, val arguments: JsonObject)

/** Google's error message from an error response, which may come wrapped in an array. */
internal fun errorMessage(body: String): String? = runCatching {
    val root = Json.parseToJsonElement(body)
    val error = ((root as? JsonArray)?.firstOrNull() ?: root).jsonObject["error"] as JsonObject
    error.string("message")
}.getOrNull()

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

val JsonObject.steps: List<JsonObject>
    get() = (this["steps"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

val JsonObject.type: String? get() = string("type")

fun JsonObject.text(): String = when (val content = this["content"]) {
    is JsonPrimitive -> content.contentOrNull.orEmpty()
    is JsonArray -> content.mapNotNull { (it as? JsonObject)?.takeIf { part -> part.type == "text" }?.string("text") }.joinToString("")
    else -> ""
}

fun List<JsonObject>.outputText(): String =
    filter { it.type == "model_output" }.map { it.text().trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")

/** The text after the last tool step: an agent's final answer, without its earlier progress notes. */
fun List<JsonObject>.finalOutputText(): String =
    takeLastWhile { it.type == "model_output" || it.type == "thought" }.outputText()

fun List<JsonObject>.functionCalls(): List<FunctionCall> =
    filter { it.type == "function_call" }.mapNotNull { step ->
        FunctionCall(
            id = step.string("id") ?: return@mapNotNull null,
            name = step.string("name") ?: return@mapNotNull null,
            arguments = step["arguments"] as? JsonObject ?: JsonObject(emptyMap()),
        )
    }
