package dorai.server.ai

import dorai.server.ai.embed.FakeEmbedder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class AiConfigTest {
    @Test
    fun defaultsToTheFakeModelWithIngestion() {
        val config = AiConfig.fromEnv(emptyMap())

        assertIs<FakeEmbedder>(config.embedder())
        assertEquals(0.40, config.minSimilarity)
        assertEquals(true, config.ingestOnStartup)
    }

    @Test
    fun readsThresholdAndIngestionSwitch() {
        val config = AiConfig.fromEnv(mapOf("DORAI_AI_MIN_SIMILARITY" to "0.5", "DORAI_AI_INGEST_ON_STARTUP" to "false"))

        assertEquals(0.5, config.minSimilarity)
        assertEquals(false, config.ingestOnStartup)
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
}
