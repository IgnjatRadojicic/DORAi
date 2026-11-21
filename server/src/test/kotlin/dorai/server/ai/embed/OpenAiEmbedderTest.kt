package dorai.server.ai.embed

import dorai.server.ai.AiConfig
import dorai.server.ai.chat.FakeOpenAi
import dorai.server.ai.chat.FakeOpenAi.Companion.respondEmbeddings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The real Koog embedding client against a local fake of the OpenAI API. */
class OpenAiEmbedderTest {
    @Test
    fun returnsOneVectorPerTextInOrder() = runBlocking {
        val api = FakeOpenAi(embed = { texts -> respondEmbeddings(texts.map { text -> FloatArray(3) { text.length.toFloat() + it } }) })
        api.use {
            val vectors = OpenAiEmbedder(apiKey = "test-key-value", baseUrl = api.baseUrl).embed(listOf("ab", "abcd"))

            assertEquals(2, vectors.size)
            assertContentEquals(floatArrayOf(2f, 3f, 4f), vectors[0])
            assertContentEquals(floatArrayOf(4f, 5f, 6f), vectors[1])
            assertEquals(2, api.requests.size)
            assertTrue(api.requests.all { "text-embedding-3-small" in it.second })
        }
    }

    @Test
    fun theModelIdNamesModelAndDimensions() {
        val embedder = OpenAiEmbedder(apiKey = "test-key-value")

        assertEquals("openai/text-embedding-3-small/1536", embedder.modelId)
        assertEquals(TextEmbedder.DIMENSIONS, embedder.dimensions)
    }

    @Test
    fun theOpenAiConfigUsesTheConfiguredBaseUrl() = runBlocking {
        FakeOpenAi().use { api ->
            val config = AiConfig.fromEnv(
                mapOf("DORAI_AI_PROVIDER" to "OpenAI", "DORAI_AI_OPENAI_KEY" to "test-key-value", "DORAI_AI_OPENAI_BASE_URL" to api.baseUrl),
            )
            val embedder = assertIs<OpenAiEmbedder>(config.embedder())

            embedder.embed(listOf("register"))

            assertEquals("embeddings", api.requests.single().first)
        }
    }
}
