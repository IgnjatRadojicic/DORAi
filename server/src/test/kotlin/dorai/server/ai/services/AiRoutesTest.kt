package dorai.server.ai.services

import dorai.server.ai.chat.ChatModel
import dorai.server.ai.chat.ChatRequest
import dorai.server.ai.chat.ChatUnavailableException
import dorai.server.ai.chat.FakeChatModel
import dorai.server.core.Services
import dorai.server.fixtures.FixtureData
import dorai.server.fixtures.InMemorySuggestionRepository
import dorai.server.fixtures.fixtureServices
import dorai.server.module
import dorai.shared.model.DoraiJson
import dorai.shared.model.Finding
import dorai.shared.model.Suggestion
import dorai.shared.model.SuggestionStatus
import dorai.shared.rules.RulesPlanChecker
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.KSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The AI services behind the real routes: what reaches a reviewer, and what the explainer may change. */
class AiRoutesTest {
    private val fixtures = FixtureData.load()
    private val search = StubSearch(annexS18, annexS17, functionsColumn, doraArticle)

    private fun services(chat: ChatModel): Services = fixtureServices(fixtures).copy(
        suggestions = InMemorySuggestionRepository(emptyList()),
        classifier = AiResourceClassifier(chat, search),
        functionSuggester = AiFunctionSuggester(chat, search),
        explainer = AiFindingExplainer(chat, search),
        reportDrafter = AiReportDrafter(chat, search),
        regulationSearch = search,
    )

    @Test
    fun generatedSuggestionsArePendingAndCited() = testApplication {
        application { module(services(FakeChatModel())) }

        val response = client.post("/api/suggestions/generate")
        val suggestions = DoraiJson.decodeFromString<List<Suggestion>>(response.bodyAsText())

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.all { it.status == SuggestionStatus.Pending && it.citations.isNotEmpty() })
    }

    @Test
    fun aModelOutageIsNeverStoredAsASuggestion() = testApplication {
        application { module(services(down)) }

        client.post("/api/suggestions/generate")
        val stored = DoraiJson.decodeFromString<List<Suggestion>>(client.get("/api/suggestions").bodyAsText())

        assertTrue(stored.isEmpty())
    }

    @Test
    fun theExplainerAddsTextAndCitationButNeverChangesAFinding() = testApplication {
        application { module(services(FakeChatModel())) }
        val plan = fixtures.planEastus
        val expected = RulesPlanChecker().check(plan.decodeToString(), fixtures.register)

        val response = client.submitFormWithBinaryData(
            "/api/check",
            formData { append("plan", plan, Headers.build { append(HttpHeaders.ContentDisposition, "filename=\"plan.json\"") }) },
        )
        val explained = DoraiJson.decodeFromString<List<Finding>>(response.bodyAsText())

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(expected, explained.map { it.copy(explanation = null, citation = null) })
        assertTrue(explained.all { it.explanation != null && it.citation != null })
    }

    private val down = object : ChatModel {
        override val modelId = "test/down"
        override suspend fun <T> ask(request: ChatRequest, serializer: KSerializer<T>): T = throw ChatUnavailableException("down")
    }
}
