package dorai.server.ai.embed

import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels

/**
 * `text-embedding-3-small` through Koog's OpenAI client. The key comes from the environment only.
 * Koog 1.3 refuses a list of texts for OpenAI, so each text is its own request, one after another.
 */
class OpenAiEmbedder(apiKey: String, baseUrl: String? = null) : TextEmbedder {
    private val model = OpenAIModels.Embeddings.TextEmbedding3Small
    private val client = if (baseUrl == null) {
        OpenAILLMClient(apiKey)
    } else {
        OpenAILLMClient(apiKey, OpenAIClientSettings(baseUrl = baseUrl))
    }

    override val modelId = "openai/${model.id}/${TextEmbedder.DIMENSIONS}"
    override val dimensions = TextEmbedder.DIMENSIONS

    override suspend fun embed(texts: List<String>): List<FloatArray> = texts.map { text ->
        val values = client.embed(text, model)
        FloatArray(values.size) { values[it].toFloat() }
    }
}
