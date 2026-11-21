package dorai.server.ai.chat

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A local stand-in for the OpenAI HTTP API on 127.0.0.1, so the real Koog client code runs in tests
 * without network or key. [chat] answers chat completions, [embed] answers embeddings.
 */
class FakeOpenAi(
    private val chat: suspend ApplicationCall.(body: String) -> Unit = { respondChat("{}") },
    private val embed: suspend ApplicationCall.(texts: List<String>) -> Unit = { texts -> respondEmbeddings(texts.map { FloatArray(3) }) },
) : AutoCloseable {
    val requests = mutableListOf<Pair<String, String>>()

    private val server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
        embeddedServer(Netty, port = 0, host = "127.0.0.1") {
            routing {
                post("/v1/chat/completions") {
                    val body = call.receiveText()
                    requests += "chat" to body
                    call.chat(body)
                }
                post("/v1/embeddings") {
                    val body = call.receiveText()
                    requests += "embeddings" to body
                    val input = Json.parseToJsonElement(body).jsonObject.getValue("input")
                    val texts = (input as? JsonArray)?.map { (it as JsonPrimitive).content } ?: listOf((input as JsonPrimitive).content)
                    call.embed(texts)
                }
            }
        }.start(wait = false)

    val baseUrl: String = "http://127.0.0.1:" + runBlocking { server.engine.resolvedConnectors().first().port }

    override fun close() = server.stop(gracePeriodMillis = 0, timeoutMillis = 1000)

    companion object {
        suspend fun ApplicationCall.respondChat(content: String) = respondText(
            buildJsonObject {
                put("id", "chatcmpl-test")
                put("object", "chat.completion")
                put("created", 1)
                put("model", "gpt-4.1-mini")
                putJsonArray("choices") {
                    add(
                        buildJsonObject {
                            put("index", 0)
                            putJsonObject("message") {
                                put("role", "assistant")
                                put("content", content)
                            }
                            put("finish_reason", "stop")
                        },
                    )
                }
                putJsonObject("usage") {
                    put("prompt_tokens", 1)
                    put("completion_tokens", 1)
                    put("total_tokens", 2)
                }
            }.toString(),
            ContentType.Application.Json,
        )

        suspend fun ApplicationCall.respondEmbeddings(vectors: List<FloatArray>) = respondText(
            buildJsonObject {
                put("object", "list")
                put("model", "text-embedding-3-small")
                putJsonArray("data") {
                    vectors.forEachIndexed { index, vector ->
                        add(
                            buildJsonObject {
                                put("object", "embedding")
                                put("index", index)
                                put("embedding", JsonArray(vector.map { JsonPrimitive(it) }))
                            },
                        )
                    }
                }
                putJsonObject("usage") {
                    put("prompt_tokens", 1)
                    put("total_tokens", 1)
                }
            }.toString(),
            ContentType.Application.Json,
        )

        suspend fun ApplicationCall.respondError(status: HttpStatusCode) =
            respondText("""{"error":{"message":"test error","type":"test"}}""", ContentType.Application.Json, status)

        /** The messages the client sent, as role to content. */
        fun messages(body: String) = Json.parseToJsonElement(body).jsonObject.getValue("messages").jsonArray.map {
            val message = it.jsonObject
            (message.getValue("role") as JsonPrimitive).content to message.getValue("content").toString()
        }
    }
}
