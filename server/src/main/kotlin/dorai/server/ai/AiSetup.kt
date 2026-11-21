package dorai.server.ai

import dorai.server.ai.embed.FakeEmbedder
import dorai.server.ai.embed.OpenAiEmbedder
import dorai.server.ai.embed.TextEmbedder
import dorai.server.ai.ingest.RegulationCorpus
import dorai.server.ai.ingest.RegulationIngestor
import dorai.server.ai.search.PgRegulationSearch
import dorai.server.core.RegulationSearch
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
 * - `DORAI_AI_MIN_SIMILARITY`: search threshold, default 0.40
 * - `DORAI_AI_INGEST_ON_STARTUP`: `true` (default) or `false`
 */
data class AiConfig(
    val provider: String = "fake",
    val openAiKey: String? = null,
    val openAiBaseUrl: String? = null,
    val minSimilarity: Double = PgRegulationSearch.DEFAULT_MIN_SIMILARITY,
    val ingestOnStartup: Boolean = true,
) {
    override fun toString() = "AiConfig(provider=$provider, minSimilarity=$minSimilarity, ingestOnStartup=$ingestOnStartup)"

    fun embedder(): TextEmbedder = when (provider) {
        "fake" -> FakeEmbedder()
        "openai" -> OpenAiEmbedder(
            apiKey = requireNotNull(openAiKey?.takeIf { it.isNotBlank() }) { "DORAI_AI_OPENAI_KEY is required for provider openai" },
            baseUrl = openAiBaseUrl,
        )
        else -> error("Unknown DORAI_AI_PROVIDER '$provider': use fake or openai")
    }

    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()) = AiConfig(
            provider = env["DORAI_AI_PROVIDER"]?.lowercase()?.takeIf { it.isNotBlank() } ?: "fake",
            openAiKey = env["DORAI_AI_OPENAI_KEY"],
            openAiBaseUrl = env["DORAI_AI_OPENAI_BASE_URL"]?.takeIf { it.isNotBlank() },
            minSimilarity = env["DORAI_AI_MIN_SIMILARITY"]?.toDoubleOrNull() ?: PgRegulationSearch.DEFAULT_MIN_SIMILARITY,
            ingestOnStartup = env["DORAI_AI_INGEST_ON_STARTUP"]?.lowercase() != "false",
        )
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

private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
