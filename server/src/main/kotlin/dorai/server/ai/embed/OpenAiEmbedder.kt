package dorai.server.ai.embed

import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels

/** `text-embedding-3-small` through Koog's OpenAI client. The key comes from the environment only. */
class OpenAiEmbedder(apiKey: String, baseUrl: String? = null) : TextEmbedder {
    private val model = OpenAIModels.Embeddings.TextEmbedding3Small
    private val client = if (baseUrl == null) {
        OpenAILLMClient(apiKey)
    } else {
        OpenAILLMClient(apiKey, OpenAIClientSettings(baseUrl = baseUrl))
    }

    override val modelId = "openai/${model.id}/${TextEmbedder.DIMENSIONS}"
    override val dimensions = TextEmbedder.DIMENSIONS

    override suspend fun embed(texts: List<String>): List<FloatArray> =
        client.embed(texts, model).map { values -> FloatArray(values.size) { values[it].toFloat() } }
}
