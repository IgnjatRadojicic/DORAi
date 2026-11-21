package dorai.server.ai

import dorai.server.ai.embed.FakeEmbedder
import dorai.server.db.DatabaseConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

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
    fun secretsNeverAppearInLogText() {
        val ai = AiConfig.fromEnv(mapOf("DORAI_AI_PROVIDER" to "openai", "DORAI_AI_OPENAI_KEY" to "test-key-value"))
        val db = DatabaseConfig("jdbc:postgresql://localhost/dorai", "dorai", "test-password-value")

        assertFalse("test-key-value" in ai.toString())
        assertFalse("test-password-value" in db.toString())
    }

    @Test
    fun noDatabaseWithoutUrl() {
        assertNull(DatabaseConfig.fromEnv(emptyMap()))
        assertEquals("dorai", DatabaseConfig.fromEnv(mapOf("DORAI_DB_URL" to "jdbc:postgresql://db/dorai"))?.user)
    }
}
