package dorai.server.ai.chat

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.executeStructured
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import dorai.server.ai.OPENAI_RETRY
import dorai.server.ai.openAiClient
import kotlinx.serialization.KSerializer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Chat model through Koog structured output: Koog sends the JSON schema of T and parses the reply.
 * No fixing parser, so Koog never asks again after a reply of the wrong shape; the services decide on that.
 * Rate limits and server errors are sent again by the client (see [openAiClient]), all within [timeout].
 */
class KoogChatModel(
    private val executor: PromptExecutor,
    private val model: LLModel,
    private val timeout: Duration = 60.seconds,
) : ChatModel {
    override val modelId = "${model.provider.id}/${model.id}"

    override suspend fun <T> ask(request: ChatRequest, serializer: KSerializer<T>): T {
        val prompt = prompt(request.task.id) {
            system(request.instructions)
            user(request.userMessage())
        }
        val result = try {
            withTimeout(timeout) { executor.executeStructured(prompt, model, serializer) }
        } catch (e: TimeoutCancellationException) {
            throw ChatUnavailableException("The chat model did not answer within $timeout", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ChatUnavailableException("The chat model could not be reached", e)
        }
        return result.getOrElse { throw InvalidReplyException("The chat model reply did not match the expected shape", it) }.data
    }

    companion object {
        const val DEFAULT_OPENAI_MODEL = "gpt-4.1-mini"

        /** OpenAI chat model by name; the key comes from the environment only. */
        fun openAi(
            apiKey: String,
            baseUrl: String? = null,
            modelName: String = DEFAULT_OPENAI_MODEL,
            retry: RetryConfig = OPENAI_RETRY,
        ): KoogChatModel {
            val model = OpenAIModels.Chat.GPT4_1Mini.copy(id = modelName)
            return KoogChatModel(MultiLLMPromptExecutor(openAiClient(apiKey, baseUrl, retry)), model)
        }
    }
}
