package dorai.server.ai

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.toRetryingClient
import kotlin.time.Duration.Companion.seconds

/**
 * Model quotas are counted per minute, so a call that hits one waits up to about a minute in total
 * (2, 4, 8, 16 and 30 seconds, give or take 20 percent) before it fails.
 */
val OPENAI_RETRY = RetryConfig(
    maxAttempts = 6,
    initialDelay = 2.seconds,
    maxDelay = 30.seconds,
    backoffMultiplier = 2.0,
    jitterFactor = 0.2,
)

/**
 * The Koog OpenAI client, also for OpenAI-compatible endpoints such as Azure OpenAI.
 * Rate limits (429), server errors (5xx) and dropped connections are tried again with [retry];
 * when the server says how long to wait ("retry after 15 seconds"), that wait is used.
 * Other errors, such as a bad request or a wrong key, fail at once.
 * A cancelled caller always gets its cancellation back, never a client error ([CancellationKeepingClient]).
 */
fun openAiClient(apiKey: String, baseUrl: String?, retry: RetryConfig = OPENAI_RETRY): LLMClient {
    val client = if (baseUrl == null) OpenAILLMClient(apiKey) else OpenAILLMClient(apiKey, OpenAIClientSettings(baseUrl = baseUrl))
    return CancellationKeepingClient(client).toRetryingClient(retry)
}
