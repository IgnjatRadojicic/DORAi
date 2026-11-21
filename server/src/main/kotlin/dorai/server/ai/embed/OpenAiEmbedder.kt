package dorai.server.ai.embed

import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.retry.RetryConfig
import dorai.server.ai.OPENAI_RETRY
import dorai.server.ai.openAiClient

/**
 * `text-embedding-3-small` through Koog's OpenAI client. The key comes from the environment only.
 * Koog 1.3 refuses a list of texts for OpenAI, so each text is its own request, one after another.
 * A request that hits the quota waits and is sent again ([retry]), so ingestion finishes on a small quota too.
 */
class OpenAiEmbedder(apiKey: String, baseUrl: String? = null, retry: RetryConfig = OPENAI_RETRY) : TextEmbedder {
    private val model = OpenAIModels.Embeddings.TextEmbedding3Small
    private val client = openAiClient(apiKey, baseUrl, retry)

    override val modelId = "openai/${model.id}/${TextEmbedder.DIMENSIONS}"
    override val dimensions = TextEmbedder.DIMENSIONS

    override suspend fun embed(texts: List<String>): List<FloatArray> = texts.map { text ->
        val values = client.embed(text, model)
        FloatArray(values.size) { values[it].toFloat() }
    }
}
