package dorai.server

import dorai.shared.api.ApiResult
import dorai.shared.api.DoraiApi
import dorai.shared.model.CostSource
import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.ScanMode
import dorai.shared.model.Severity
import dorai.shared.model.SuggestionStatus
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Calls the real server through the real shared client, so the two can't drift apart. */
class ContractTest {

    private fun contract(block: suspend (DoraiApi) -> Unit) = testApplication {
        application { module() }
        DoraiApi("http://localhost", client).use { block(it) }
    }

    private fun <T> ApiResult<T>.ok(): T = when (this) {
        is ApiResult.Success -> value
        is ApiResult.Failure -> fail("Expected success, got $problem")
    }

    private fun ApiResult<*>.failure(): ApiResult.Failure = assertIs<ApiResult.Failure>(this)

    @Test
    fun health() = contract { api -> assertEquals("ok", api.health().ok().status) }

    @Test
    fun scanAndResources() = contract { api ->
        val scan = api.scan().ok()
        assertEquals(ScanMode.Fixture, scan.mode)
        assertEquals(CostSource.Fixture, scan.costSource)
        assertEquals(19, scan.resourceCount)
        assertEquals(3, scan.newSuggestionCount)

        val resources = api.resources().ok()
        assertEquals(19, resources.size)
        val payments = resources.single { it.name == "psql-finpay-payments-2udl" }
        assertEquals("F1", payments.functionId)
        assertEquals("C1", payments.contractId)
        assertNotNull(payments.monthlyCost)
        assertEquals("EUR", payments.currency)
    }

    @Test
    fun registerAndValidation() = contract { api ->
        val register = api.register().ok()
        assertEquals(5, register.functions.size)
        assertEquals(2, register.providers.size)
        val cost = register.contracts.first { it.id == "C1" }.annualCost
        assertEquals("40000", cost.value)
        assertEquals(ProvenanceLabel.HumanConfirmed, cost.provenance.label)

        assertTrue(api.validation().ok().isValid)
    }

    @Test
    fun concentrationSharesAddUp() = contract { api ->
        val report = api.concentration().ok()
        assertTrue(report.totalCost > 0)
        assertTrue(abs(report.byProvider.sumOf { it.share } - 1.0) < 0.001)
        assertTrue(report.byFunction.any { it.key == "F1" && it.criticalFunction })
        assertTrue(report.from < report.to)
    }

    @Test
    fun suggestionsCanBeDecidedOnce() = contract { api ->
        val pending = api.suggestions().ok()
        assertEquals(3, pending.size)
        assertTrue(pending.all { it.status == SuggestionStatus.Pending && it.citations.isNotEmpty() })

        val first = pending.first()
        val accepted = api.acceptSuggestion(first.id).ok()
        assertEquals(SuggestionStatus.Accepted, accepted.status)
        assertNotNull(accepted.decidedAt)

        assertEquals(409, api.rejectSuggestion(first.id).failure().problem.status)
        assertEquals(2, api.generateSuggestions().ok().size)
        assertEquals(1, api.generateSuggestions(pending.last().resourceId).ok().size)

        val missing = api.acceptSuggestion("nope").failure().problem
        assertEquals(404, missing.status)
        assertEquals("Suggestion not found", missing.title)
    }

    @Test
    fun checkReturnsTheDemoFindings() = contract { api ->
        val findings = api.check().ok()
        assertEquals(4, findings.size)
        assertEquals(2, findings.count { it.severity == Severity.High })

        assertEquals(4, api.check("""{"resource_changes":[]}""".encodeToByteArray()).ok().size)
        assertEquals(400, api.check("not json".encodeToByteArray()).failure().problem.status)
    }

    @Test
    fun exportIsNotAvailableYet() = contract { api ->
        assertEquals(501, api.exportCsv().failure().problem.status)
        assertEquals(501, api.exportPdf().failure().problem.status)
    }

    @Test
    fun unknownRouteIsAProblem() = testApplication {
        application { module() }
        val response = client.get("/api/nope")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.headers[HttpHeaders.ContentType].orEmpty().startsWith("application/problem+json"))
        assertTrue(response.bodyAsText().contains("\"status\":404"))
    }
}
