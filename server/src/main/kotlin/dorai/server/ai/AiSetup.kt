package dorai.server.ai

import dorai.server.ai.chat.ChatModel
import dorai.server.ai.chat.FakeChatModel
import dorai.server.ai.chat.KoogChatModel
import dorai.server.ai.embed.FakeEmbedder
import dorai.server.ai.embed.OpenAiEmbedder
import dorai.server.ai.embed.TextEmbedder
import dorai.server.ai.ingest.RegulationCorpus
import dorai.server.ai.ingest.RegulationIngestor
import dorai.server.ai.search.PgRegulationSearch
import dorai.server.ai.services.AiFindingExplainer
import dorai.server.ai.services.AiFunctionSuggester
import dorai.server.ai.services.AiReportDrafter
import dorai.server.ai.services.AiResourceClassifier
import dorai.server.core.RegulationSearch
import dorai.server.core.Services
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory

/**
 * AI settings from the environment. The fake model is the default, so nobody needs a key to work.
 *
 * - `DORAI_AI_PROVIDER`: `fake` (default) or `openai`
 * - `DORAI_AI_OPENAI_KEY`, `DORAI_AI_OPENAI_BASE_URL` (optional): for `openai`
 * - `DORAI_AI_CHAT_MODEL`: OpenAI chat model, default `gpt-4.1-mini`
 * - `DORAI_AI_MIN_SIMILARITY`: search threshold, default 0.40 for `openai` and 0.25 for `fake`
 * - `DORAI_AI_INGEST_ON_STARTUP`: `true` (default) or `false`
 */
data class AiConfig(
    val provider: String = "fake",
    val openAiKey: String? = null,
    val openAiBaseUrl: String? = null,
    val chatModelName: String = KoogChatModel.DEFAULT_OPENAI_MODEL,
    val minSimilarity: Double = defaultMinSimilarity(provider),
    val ingestOnStartup: Boolean = true,
) {
    override fun toString() = "AiConfig(provider=$provider, chatModel=$chatModelName, minSimilarity=$minSimilarity, ingestOnStartup=$ingestOnStartup)"

    fun embedder(): TextEmbedder = when (provider) {
        "fake" -> FakeEmbedder()
        "openai" -> OpenAiEmbedder(
            apiKey = requireKey(),
            baseUrl = openAiBaseUrl,
        )
        else -> error("Unknown DORAI_AI_PROVIDER '$provider': use fake or openai")
    }

    fun chatModel(): ChatModel = when (provider) {
        "fake" -> FakeChatModel()
        "openai" -> KoogChatModel.openAi(apiKey = requireKey(), baseUrl = openAiBaseUrl, modelName = chatModelName)
        else -> error("Unknown DORAI_AI_PROVIDER '$provider': use fake or openai")
    }

    private fun requireKey(): String =
        requireNotNull(openAiKey?.takeIf { it.isNotBlank() }) { "DORAI_AI_OPENAI_KEY is required for provider openai" }

    companion object {
        /** The fake embedder hashes words, so related passages score lower than with a real model: the right chunk ranks first at about 0.3. */
        const val FAKE_MIN_SIMILARITY = 0.25

        fun defaultMinSimilarity(provider: String) =
            if (provider == "fake") FAKE_MIN_SIMILARITY else PgRegulationSearch.DEFAULT_MIN_SIMILARITY

        fun fromEnv(env: Map<String, String> = System.getenv()): AiConfig {
            val provider = env["DORAI_AI_PROVIDER"]?.lowercase()?.takeIf { it.isNotBlank() } ?: "fake"
            return AiConfig(
                provider = provider,
                openAiKey = env["DORAI_AI_OPENAI_KEY"],
                openAiBaseUrl = env["DORAI_AI_OPENAI_BASE_URL"]?.takeIf { it.isNotBlank() },
                chatModelName = env["DORAI_AI_CHAT_MODEL"]?.takeIf { it.isNotBlank() } ?: KoogChatModel.DEFAULT_OPENAI_MODEL,
                minSimilarity = env["DORAI_AI_MIN_SIMILARITY"]?.toDoubleOrNull() ?: defaultMinSimilarity(provider),
                ingestOnStartup = env["DORAI_AI_INGEST_ON_STARTUP"]?.lowercase() != "false",
            )
        }
    }
}

private val log = LoggerFactory.getLogger("dorai.server.ai")

/**
 * Regulation search on the database. When enabled, the corpus is ingested in the background so the
 * server answers at once; a failed ingestion is logged and never stops the server.
 */
fun regulationSearch(database: Database, config: AiConfig, scope: CoroutineScope = backgroundScope): RegulationSearch {
    val embedder = config.embedder()
    log.info("Regulation search with {} ({})", embedder.modelId, config)
    if (config.ingestOnStartup) {
        scope.launch {
            try {
                val result = RegulationIngestor(database, embedder).ingest(RegulationCorpus.chunks())
                log.info("Regulation ingestion: {} inserted, {} removed, {} chunks", result.inserted, result.removed, result.total)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Regulation ingestion failed; search answers from what is stored", e)
            }
        }
    }
    return PgRegulationSearch(database, embedder, config.minSimilarity)
}

/**
 * Replaces the stand-in AI with the real services: regulation search on the database, the chat model
 * from [config], and the classifier, function suggester, explainer and report drafter on top of both.
 */
fun Services.withAi(database: Database, config: AiConfig, scope: CoroutineScope = backgroundScope): Services {
    val search = regulationSearch(database, config, scope)
    val chat = config.chatModel()
    log.info("AI services with chat model {}", chat.modelId)
    return copy(
        classifier = AiResourceClassifier(chat, search),
        functionSuggester = AiFunctionSuggester(chat, search),
        explainer = AiFindingExplainer(chat, search),
        reportDrafter = AiReportDrafter(chat, search),
        regulationSearch = search,
    )
}

private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
