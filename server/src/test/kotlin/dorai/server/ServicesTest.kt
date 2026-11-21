package dorai.server

import dorai.server.core.AiAnswer
import dorai.server.core.FindingExplainer
import dorai.server.core.ResourceClassifier
import dorai.server.core.Services
import dorai.server.fixtures.fixtureServices
import dorai.shared.api.ApiResult
import dorai.shared.api.DoraiApi
import dorai.shared.model.Citation
import dorai.shared.model.Finding
import dorai.shared.model.Resource
import dorai.shared.model.ServiceType
import dorai.shared.model.SuggestionKind
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** The routes reach every lane only through [Services], so swapping one implementation changes the answers. */
class ServicesTest {

    private val citation = Citation("c1", "Regulation (EU) 2022/2554 (DORA)", "Article 28", "3", "register of information", 0.5)

    private fun withServices(services: Services, block: suspend (DoraiApi) -> Unit) = testApplication {
        application { module(services) }
        DoraiApi("http://localhost", client).use { block(it) }
    }

    private fun <T> ApiResult<T>.ok(): T = (this as? ApiResult.Success)?.value ?: fail("Expected success, got $this")

    @Test
    fun anAiAnswerMustCite() {
        assertFailsWith<IllegalArgumentException> { AiAnswer.Answer("x", "reason", citations = emptyList()) }
    }

    @Test
    fun withoutAiNothingNewIsSuggested() = withServices(fixtureServices()) { api ->
        assertEquals(3, api.generateSuggestions().ok().size)
        assertEquals(404, (api.generateSuggestions("missing") as ApiResult.Failure).problem.status)
    }

    @Test
    fun classifierAnswersBecomePendingSuggestions() {
        val classifier = object : ResourceClassifier {
            override suspend fun classify(resource: Resource) = AiAnswer.Answer(ServiceType.S18, "platform service", listOf(citation))
        }
        withServices(fixtureServices().copy(classifier = classifier)) { api ->
            val pending = api.generateSuggestions().ok()
            // 9 resources have a type the service type table does not know; one already has a suggestion in the fixtures.
            assertEquals(3 + 8, pending.size)
            assertTrue(pending.filter { it.kind == SuggestionKind.ServiceType }.all { it.citations.isNotEmpty() })
            assertEquals(11, api.generateSuggestions().ok().size, "a second run must not duplicate")
        }
    }

    @Test
    fun explainerAddsTextButNeverChangesFindings() {
        val explainer = object : FindingExplainer {
            override suspend fun explain(finding: Finding) = AiAnswer.Answer("Because DORA says so.", "r", listOf(citation))
        }
        withServices(fixtureServices().copy(explainer = explainer)) { api ->
            val findings = api.check().ok()
            assertEquals(4, findings.size)
            assertTrue(findings.all { it.explanation == "Because DORA says so." && it.citation == citation })
            assertEquals(2, findings.count { it.severity.name == "High" })
        }
    }

    @Test
    fun noExporterMeansNotImplemented() = withServices(fixtureServices().copy(exporter = null)) { api ->
        assertIs<ApiResult.Failure>(api.exportCsv()).also { assertEquals(501, it.problem.status) }
    }
}
