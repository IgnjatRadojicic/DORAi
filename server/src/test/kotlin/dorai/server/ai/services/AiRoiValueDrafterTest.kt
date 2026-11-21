package dorai.server.ai.services

import dorai.server.ai.chat.FakeChatModel
import dorai.server.core.AiAnswer
import dorai.server.fixtures.FixtureData
import dorai.server.fixtures.fixtureServices
import dorai.server.module
import dorai.server.review.RoiDrafts
import dorai.shared.api.ApiResult
import dorai.shared.api.DoraiApi
import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.SuggestionKind
import dorai.shared.roi.RoiSpec
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AiRoiValueDrafterTest {

    private val reliance = RoiSpec.column("B_02.02.0180")!!
    private val relianceColumn = citation("CIR-2024-2956/AnnexI/B_02.02.0180", article = "Annex I", excerpt = "Level of reliance")

    @Test
    fun `a cited option becomes the draft`() = runTest {
        val chat = ScriptedChat("""{"value":"4","reason":"Payments stop without it.","confidence":0.7,"citationIds":["${relianceColumn.id}"]}""")
        val answer = assertIs<AiAnswer.Answer<String>>(AiRoiValueDrafter(chat, StubSearch(relianceColumn)).draftValue(reliance, "C1 S18", mapOf("critical" to "true")))
        assertEquals("4" to listOf(relianceColumn), answer.value to answer.citations)
    }

    @Test
    fun `a value outside the options is retried and then refused`() = runTest {
        val bad = """{"value":"Full","reason":"x","confidence":0.7,"citationIds":["${relianceColumn.id}"]}"""
        val answer = AiRoiValueDrafter(ScriptedChat(bad, bad), StubSearch(relianceColumn)).draftValue(reliance, "C1", emptyMap())
        assertIs<AiAnswer.Failed>(answer)
    }

    @Test
    fun `unknown and no evidence are insufficient evidence`() = runTest {
        val unknown = """{"value":"unknown","reason":"Not enough facts.","confidence":0.1,"citationIds":[]}"""
        assertIs<AiAnswer.InsufficientEvidence>(AiRoiValueDrafter(ScriptedChat(unknown), StubSearch(relianceColumn)).draftValue(reliance, "C1", emptyMap()))
        assertIs<AiAnswer.InsufficientEvidence>(AiRoiValueDrafter(ScriptedChat(), StubSearch()).draftValue(reliance, "C1", emptyMap()))
    }

    @Test
    fun `the fake model drafts from criticality`() = runTest {
        val answer = AiRoiValueDrafter(FakeChatModel(), StubSearch(relianceColumn)).draftValue(reliance, "C1", mapOf("critical" to "true"))
        assertEquals("3", assertIs<AiAnswer.Answer<String>>(answer).value)
    }

    @Test
    fun `only empty required judgement cells are drafted, once`() {
        val data = FixtureData.load()
        val date = LocalDate(2026, 12, 31)
        val targets = RoiDrafts.targets(data.register, data.resources, date, emptySet())
        assertTrue(targets.isNotEmpty())
        assertTrue(targets.all { it.column.code in RoiDrafts.DRAFTABLE })
        val first = targets.first()
        val again = RoiDrafts.targets(data.register, data.resources, date, setOf(first.itemId to first.column.code))
        assertTrue(again.none { it.itemId == first.itemId && it.column.code == first.column.code })
    }

    @Test
    fun `generate adds RoI drafts and accepting one writes it as the reviewer's value`() = testApplication {
        val drafter = AiRoiValueDrafter(FakeChatModel(), StubSearch(relianceColumn))
        application { module(fixtureServices().copy(roiDrafter = drafter)) }
        DoraiApi("http://localhost", client).use { api ->
            val pending = assertIs<ApiResult.Success<List<dorai.shared.model.Suggestion>>>(api.generateSuggestions()).value
            val draft = pending.first { it.kind == SuggestionKind.RoiValue && it.column == "B_02.02.0180" }
            assertTrue(draft.citations.isNotEmpty())
            assertIs<ApiResult.Success<*>>(api.acceptSuggestion(draft.id))
            val register = assertIs<ApiResult.Success<dorai.shared.model.Register>>(api.register()).value
            val stored = register.contracts.flatMap { it.services }.single { it.id == draft.resourceId }.roi.getValue("B_02.02.0180")
            assertEquals(draft.value to ProvenanceLabel.HumanConfirmed, stored.value to stored.provenance.label)
        }
    }
}
