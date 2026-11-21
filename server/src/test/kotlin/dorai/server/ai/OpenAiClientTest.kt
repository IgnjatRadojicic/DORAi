package dorai.server.ai

import ai.koog.prompt.executor.clients.LLMClientException
import dorai.server.ai.chat.ChatRequest
import dorai.server.ai.chat.FakeOpenAi
import dorai.server.ai.chat.FakeOpenAi.Companion.FAST_RETRY
import dorai.server.ai.chat.FakeOpenAi.Companion.respondChat
import dorai.server.ai.chat.FakeOpenAi.Companion.respondEmbeddings
import dorai.server.ai.chat.FakeOpenAi.Companion.respondError
import dorai.server.ai.chat.FakeOpenAi.Companion.respondRateLimit
import dorai.server.ai.chat.AiTask
import dorai.server.ai.chat.KoogChatModel
import dorai.server.ai.embed.OpenAiEmbedder
import dorai.server.ai.services.ExplanationReply
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Rate limits and outages of the model API are sent again; everything else fails at once. */
class OpenAiClientTest {
    private val vector = floatArrayOf(1f, 2f, 3f)

    /** A fake whose embeddings endpoint fails the first [failures] calls with [fail], then answers. */
    private fun embeddings(failures: Int, fail: suspend io.ktor.server.application.ApplicationCall.() -> Unit): FakeOpenAi {
        val calls = AtomicInteger()
        return FakeOpenAi(embed = { texts -> if (calls.incrementAndGet() <= failures) fail() else respondEmbeddings(texts.map { vector }) })
    }

    @Test
    fun aRateLimitIsSentAgainUntilItPasses() = runBlocking {
        embeddings(failures = 2) { respondRateLimit() }.use { api ->
            val vectors = OpenAiEmbedder("test-key-value", api.baseUrl, FAST_RETRY).embed(listOf("register"))

            assertContentEquals(vector, vectors.single())
            assertEquals(3, api.requests.size)
        }
    }

    @Test
    fun theWaitTheServerNamesIsUsedByTheConfiguredEmbedder() = runBlocking {
        embeddings(failures = 1) { respondRateLimit(retryAfterSeconds = 1) }.use { api ->
            val config = AiConfig.fromEnv(
                mapOf("DORAI_AI_PROVIDER" to "openai", "DORAI_AI_OPENAI_KEY" to "test-key-value", "DORAI_AI_OPENAI_BASE_URL" to api.baseUrl),
            )
            val start = TimeSource.Monotonic.markNow()
            config.embedder().embed(listOf("register"))
            val waited = start.elapsedNow()

            assertEquals(2, api.requests.size)
            // One second from the hint, not the 2 second (plus or minus 20 percent) first backoff.
            assertTrue(waited >= 700.milliseconds && waited < 1500.milliseconds, "waited $waited")
        }
    }

    @Test
    fun itGivesUpAfterTheLastAttempt() {
        FakeOpenAi(embed = { respondRateLimit() }).use { api ->
            assertFailsWith<LLMClientException> {
                runBlocking { OpenAiEmbedder("test-key-value", api.baseUrl, FAST_RETRY).embed(listOf("register")) }
            }
            assertEquals(FAST_RETRY.maxAttempts, api.requests.size)
        }
    }

    @Test
    fun aServerErrorIsSentAgain() = runBlocking {
        embeddings(failures = 1) { respondError(HttpStatusCode.ServiceUnavailable) }.use { api ->
            OpenAiEmbedder("test-key-value", api.baseUrl, FAST_RETRY).embed(listOf("register"))
            assertEquals(2, api.requests.size)
        }
    }

    @Test
    fun aBadRequestFailsAtOnce() {
        FakeOpenAi(embed = { respondError(HttpStatusCode.BadRequest) }).use { api ->
            assertFailsWith<LLMClientException> {
                runBlocking { OpenAiEmbedder("test-key-value", api.baseUrl, FAST_RETRY).embed(listOf("register")) }
            }
            assertEquals(1, api.requests.size)
        }
    }

    @Test
    fun theChatModelSendsARateLimitedCallAgain() = runBlocking {
        val calls = AtomicInteger()
        val api = FakeOpenAi(chat = {
            if (calls.incrementAndGet() == 1) respondRateLimit() else respondChat("""{"explanation":"It matters.","citationIds":["DORA/Art-28/3"]}""")
        })
        api.use {
            val request = ChatRequest(AiTask.ExplainFinding, "Task: explain-finding", buildJsonObject { })
            val reply = KoogChatModel.openAi("test-key-value", api.baseUrl, retry = FAST_RETRY).ask(request, ExplanationReply.serializer())

            assertEquals(ExplanationReply("It matters.", listOf("DORA/Art-28/3")), reply)
            assertEquals(2, api.requests.size)
        }
    }

    @Test
    fun cancellingTheCallerStopsTheWait() {
        FakeOpenAi(embed = { respondRateLimit(retryAfterSeconds = 30) }).use { api ->
            runBlocking {
                var outcome: Throwable? = null
                val call = launch {
                    try {
                        OpenAiEmbedder("test-key-value", api.baseUrl).embed(listOf("register"))
                    } catch (e: Throwable) {
                        outcome = e
                        throw e
                    }
                }
                while (api.requests.isEmpty()) delay(10)
                withTimeout(2.seconds) { call.cancelAndJoin() }

                assertIs<kotlinx.coroutines.CancellationException>(outcome)
                assertEquals(1, api.requests.size)
            }
        }
    }
}
