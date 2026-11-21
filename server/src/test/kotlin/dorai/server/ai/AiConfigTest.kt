package dorai.server.ai

import dorai.server.ai.chat.FakeChatModel
import dorai.server.ai.chat.KoogChatModel
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
}
