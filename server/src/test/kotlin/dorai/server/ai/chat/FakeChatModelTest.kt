package dorai.server.ai.chat

import dorai.server.ai.services.ClassificationReply
import dorai.server.ai.services.ExplanationReply
import dorai.server.ai.services.FunctionReply
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The offline stand-in: which passage it cites and when it gives up, without the services around it. */
class FakeChatModelTest {
    private val fake = FakeChatModel()

    private fun request(task: AiTask, data: JsonObject) = ChatRequest(task, "instructions", data)

    private fun evidence(vararg ids: String) = buildJsonObject {
        putJsonArray("evidence") { ids.forEach { addJsonObject { put("id", it) } } }
    }

    private fun withResource(type: String, name: String = "res", evidence: JsonObject) = JsonObject(
        evidence + buildJsonObject {
            putJsonObject("resource") {
                put("type", type)
                put("name", name)
            }
        },
    )

    @Test
    fun classifyPrefersTheMatchingAnnexEntryThenAnyAnnexEntryThenAnyPassage() = runTest {
        suspend fun cited(vararg ids: String) =
            fake.ask(request(AiTask.ClassifyServiceType, withResource("aws_s3_bucket", evidence = evidence(*ids))), ClassificationReply.serializer())

        assertEquals(listOf("CIR/AnnexIII/S17"), cited("DORA/Art-28/3", "CIR/AnnexIII/S18", "CIR/AnnexIII/S17").citationIds)
        assertEquals(listOf("CIR/AnnexIII/S18"), cited("DORA/Art-28/3", "CIR/AnnexIII/S18").citationIds)
        assertEquals(listOf("DORA/Art-28/3"), cited("DORA/Art-28/3").citationIds)
        assertEquals("S17", cited("DORA/Art-28/3").serviceType)
    }

    @Test
    fun anAwsDbInstanceIsAManagedDatabase() = runTest {
        val reply = fake.ask(request(AiTask.ClassifyServiceType, withResource("aws_db_instance", evidence = evidence("x"))), ClassificationReply.serializer())

        assertEquals("S18", reply.serviceType)
    }

    @Test
    fun platformRulesWinOverInfrastructureRules() = runTest {
        val reply = fake.ask(request(AiTask.ClassifyServiceType, withResource("azurerm_mssql_server_storage", evidence = evidence("x"))), ClassificationReply.serializer())

        assertEquals("S18", reply.serviceType)
    }

    @Test
    fun classifyAnswersUnknownWithoutCitationForAnUnmatchedType() = runTest {
        val reply = fake.ask(request(AiTask.ClassifyServiceType, withResource("contoso.widgets", evidence = evidence("x"))), ClassificationReply.serializer())

        assertEquals("Unknown", reply.serviceType)
        assertTrue(reply.citationIds.isEmpty())
    }

    @Test
    fun suggestAnswersUnknownWhenNoFunctionMatches() = runTest {
        val data = JsonObject(
            withResource("microsoft.web/sites", "app-x", evidence("x")) + buildJsonObject {
                putJsonArray("functions") { addJsonObject { put("id", "F1"); put("name", "Card payment processing") } }
            },
        )
        val reply = fake.ask(request(AiTask.SuggestFunction, data), FunctionReply.serializer())

        assertEquals("unknown", reply.functionId)
        assertTrue(reply.citationIds.isEmpty())
    }

    @Test
    fun explainWithoutTemplatesSpeaksOfTheWholeRegister() = runTest {
        val data = JsonObject(evidence("x") + buildJsonObject { putJsonObject("finding") { put("title", "New provider") } })
        val reply = fake.ask(request(AiTask.ExplainFinding, data), ExplanationReply.serializer())

        assertTrue("the register of information" in reply.explanation)
        assertEquals(listOf("x"), reply.citationIds)
    }

    @Test
    fun aReplyTypeThatDoesNotFitTheTaskIsAnInvalidReply() = runTest {
        assertFailsWith<InvalidReplyException> {
            fake.ask(request(AiTask.ClassifyServiceType, withResource("aws_s3_bucket", evidence = evidence("x"))), ExplanationReply.serializer())
        }
    }
}
