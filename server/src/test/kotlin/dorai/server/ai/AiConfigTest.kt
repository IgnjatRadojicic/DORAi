package dorai.server.ai

import dorai.server.ai.chat.FakeChatModel
import dorai.server.ai.chat.KoogChatModel
import dorai.server.ai.embed.FakeEmbedder
import dorai.server.ai.search.PgRegulationSearch
import dorai.server.ai.services.AiFindingExplainer
import dorai.server.ai.services.AiFunctionSuggester
import dorai.server.ai.services.AiReportDrafter
import dorai.server.ai.services.AiResourceClassifier
import dorai.server.fixtures.FixtureData
import dorai.server.fixtures.fixtureServices
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertSame

class AiConfigTest {
    @Test
    fun defaultsToTheFakeModelWithIngestion() {
        val config = AiConfig.fromEnv(emptyMap())

        assertIs<FakeEmbedder>(config.embedder())
        assertEquals(AiConfig.FAKE_MIN_SIMILARITY, config.minSimilarity)
        assertEquals(true, config.ingestOnStartup)
    }

    @Test
    fun readsThresholdAndIngestionSwitch() {
        val config = AiConfig.fromEnv(mapOf("DORAI_AI_MIN_SIMILARITY" to "0.5", "DORAI_AI_INGEST_ON_STARTUP" to "false"))

        assertEquals(0.5, config.minSimilarity)
        assertEquals(false, config.ingestOnStartup)
    }

    @Test
    fun openAiKeepsTheThresholdMeasuredForItsEmbeddingModel() {
        val config = AiConfig.fromEnv(mapOf("DORAI_AI_PROVIDER" to "openai", "DORAI_AI_OPENAI_KEY" to "test-key-value"))

        assertEquals(0.40, config.minSimilarity)
    }

    @Test
    fun openAiWithoutKeyOrUnknownProviderFails() {
        assertFailsWith<IllegalArgumentException> { AiConfig.fromEnv(mapOf("DORAI_AI_PROVIDER" to "openai")).embedder() }
        assertFailsWith<IllegalStateException> { AiConfig.fromEnv(mapOf("DORAI_AI_PROVIDER" to "other")).embedder() }
    }

    @Test
    fun theKeyNeverAppearsInLogText() {
        val ai = AiConfig.fromEnv(mapOf("DORAI_AI_PROVIDER" to "openai", "DORAI_AI_OPENAI_KEY" to "test-key-value"))

        assertFalse("test-key-value" in ai.toString())
    }

    @Test
    fun chatDefaultsToTheFakeModelAndReadsTheModelName() {
        assertIs<FakeChatModel>(AiConfig.fromEnv(emptyMap()).chatModel())
        assertEquals(KoogChatModel.DEFAULT_OPENAI_MODEL, AiConfig.fromEnv(emptyMap()).chatModelName)

        val config = AiConfig.fromEnv(mapOf("DORAI_AI_PROVIDER" to "openai", "DORAI_AI_OPENAI_KEY" to "test-key-value", "DORAI_AI_CHAT_MODEL" to "gpt-5.4-mini"))
        assertEquals("openai/gpt-5.4-mini", config.chatModel().modelId)
    }

    @Test
    fun openAiChatWithoutKeyFails() {
        assertFailsWith<IllegalArgumentException> { AiConfig.fromEnv(mapOf("DORAI_AI_PROVIDER" to "openai")).chatModel() }
    }

    @Test
    fun blankOrMixedCaseProviderAndBadThresholdFallBackToSafeDefaults() {
        assertEquals("fake", AiConfig.fromEnv(mapOf("DORAI_AI_PROVIDER" to "  ")).provider)
        assertEquals("openai", AiConfig.fromEnv(mapOf("DORAI_AI_PROVIDER" to "OpenAI")).provider)
        assertEquals(AiConfig.FAKE_MIN_SIMILARITY, AiConfig.fromEnv(mapOf("DORAI_AI_MIN_SIMILARITY" to " ")).minSimilarity)
        assertNull(AiConfig.fromEnv(mapOf("DORAI_AI_OPENAI_BASE_URL" to "")).openAiBaseUrl)
        assertEquals(KoogChatModel.DEFAULT_OPENAI_MODEL, AiConfig.fromEnv(mapOf("DORAI_AI_CHAT_MODEL" to "")).chatModelName)
    }

    @Test
    fun aThresholdOutsideZeroToOneOrNotANumberStopsTheServer() {
        listOf("high", "-0.1", "1.5", "NaN", "Infinity").forEach { value ->
            assertFailsWith<IllegalArgumentException>(value) { AiConfig.fromEnv(mapOf("DORAI_AI_MIN_SIMILARITY" to value)) }
        }
        assertFailsWith<IllegalArgumentException> { AiConfig(minSimilarity = 2.0) }
    }

    @Test
    fun theThresholdLimitsAreAllowed() {
        assertEquals(0.0, AiConfig.fromEnv(mapOf("DORAI_AI_MIN_SIMILARITY" to "0")).minSimilarity)
        assertEquals(1.0, AiConfig.fromEnv(mapOf("DORAI_AI_MIN_SIMILARITY" to " 1.0 ")).minSimilarity)
    }

    @Test
    fun anUnknownProviderHasNoChatModel() {
        assertFailsWith<IllegalStateException> { AiConfig.fromEnv(mapOf("DORAI_AI_PROVIDER" to "other")).chatModel() }
    }

    @Test
    fun withAiReplacesEveryStandInAndKeepsTheRest() {
        val before = fixtureServices(FixtureData.load())
        val after = before.withAi(unreachableDatabase(), AiConfig(ingestOnStartup = false), CoroutineScope(Job()))

        assertIs<AiResourceClassifier>(after.classifier)
        assertIs<AiFunctionSuggester>(after.functionSuggester)
        assertIs<AiFindingExplainer>(after.explainer)
        assertIs<AiReportDrafter>(after.reportDrafter)
        assertIs<PgRegulationSearch>(after.regulationSearch)
        assertSame(before.suggestions, after.suggestions)
        assertSame(before.exporter, after.exporter)
    }

    @Test
    fun aFailedStartupIngestionIsLoggedAndNeverStopsTheServer() = runBlocking {
        var escaped: Throwable? = null
        val scope = CoroutineScope(Job() + CoroutineExceptionHandler { _, e -> escaped = e })

        val search = regulationSearch(unreachableDatabase(), AiConfig(ingestOnStartup = true), scope)
        val ingestion = scope.coroutineContext.job.children.single()
        ingestion.join()

        assertIs<PgRegulationSearch>(search)
        assertNull(escaped)
        assertFalse(ingestion.isCancelled)
    }

    @Test
    fun stoppingTheServerCancelsTheIngestionQuietly() = runBlocking {
        var escaped: Throwable? = null
        val scope = CoroutineScope(Job() + CoroutineExceptionHandler { _, e -> escaped = e })

        regulationSearch(unreachableDatabase(), AiConfig(ingestOnStartup = true), scope)
        val ingestion = scope.coroutineContext.job.children.single()
        delay(100)
        scope.coroutineContext.job.cancel()
        ingestion.join()

        assertTrue(ingestion.isCancelled)
        assertNull(escaped)
    }

    @Test
    fun theDefaultScopeServesTheRunningServer() {
        val services = fixtureServices(FixtureData.load()).withAi(unreachableDatabase(), AiConfig(ingestOnStartup = false))

        assertIs<PgRegulationSearch>(services.regulationSearch)
        assertIs<PgRegulationSearch>(regulationSearch(unreachableDatabase(), AiConfig(ingestOnStartup = false)))
    }

    /** A database nothing listens on: connecting fails only when a query runs. */
    private fun unreachableDatabase() =
        Database.connect("jdbc:postgresql://127.0.0.1:1/none?connectTimeout=1", driver = "org.postgresql.Driver", user = "none", password = "none")
}
