package dorai.server.ai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow

/**
 * Koog 1.3's OpenAI client catches every exception of a request, the caller's cancellation included,
 * and throws it again as an [LLMClientException] (for example in `embed` and the Responses API path).
 * A cancelled caller would then see a client error instead of its cancellation, and structured
 * concurrency would treat it as a failure. When a call fails while the caller is cancelled, this
 * throws the caller's cancellation instead. It sits inside the retrying client, which never retries
 * a cancellation.
 */
internal class CancellationKeepingClient(private val delegate: LLMClient) : LLMClient() {
    override val clientName: String get() = delegate.clientName

    override fun llmProvider(): LLMProvider = delegate.llmProvider()

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant =
        keepCancellation { delegate.execute(prompt, model, tools) }

    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> =
        delegate.executeStreaming(prompt, model, tools)

    override suspend fun executeMultipleChoices(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): LLMChoice =
        keepCancellation { delegate.executeMultipleChoices(prompt, model, tools) }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        keepCancellation { delegate.moderate(prompt, model) }

    override suspend fun models(): List<LLModel> = keepCancellation { delegate.models() }

    override suspend fun embed(text: String, model: LLModel): List<Double> =
        keepCancellation { delegate.embed(text, model) }

    override suspend fun embed(inputs: List<String>, model: LLModel): List<List<Double>> =
        keepCancellation { delegate.embed(inputs, model) }

    override fun close() = delegate.close()

    override fun getStandardJsonSchemaGenerator(): StandardJsonSchemaGenerator = delegate.getStandardJsonSchemaGenerator()

    override fun getBasicJsonSchemaGenerator(): BasicJsonSchemaGenerator = delegate.getBasicJsonSchemaGenerator()

    private suspend inline fun <T> keepCancellation(block: () -> T): T =
        try {
            block()
        } catch (e: LLMClientException) {
            currentCoroutineContext().ensureActive()
            throw e
        }
}
