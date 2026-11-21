package dorai.server.register

import dorai.server.auth.Access
import dorai.server.fixtures.fixtureServices
import dorai.server.module
import dorai.server.register.lei.GleifJson
import dorai.server.register.lei.LeiDirectory
import dorai.server.register.lei.LeiLookup
import dorai.server.register.lei.LeiRecord
import dorai.shared.api.ApiResult
import dorai.shared.api.DoraiApi
import dorai.shared.model.Provenance
import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.Provider
import dorai.shared.model.RegisterField
import dorai.shared.model.Role
import dorai.shared.model.SuggestionKind
import io.ktor.http.HttpMethod
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class LeiLookupTest {

    private val now = Instant.parse("2026-11-21T12:00:00Z")
    private val stripe = LeiRecord("549300STRIPE0000IE12", "Stripe Payments Europe, Limited", "IE", "ACTIVE")

    private class FakeDirectory(
        val records: List<LeiRecord>,
        val parents: Map<String, String> = emptyMap(),
    ) : LeiDirectory {
        override suspend fun search(legalName: String) = records
        override suspend fun record(lei: String) = records.find { it.lei == lei }
        override suspend fun ultimateParent(lei: String) = parents[lei]
    }

    private fun provider(name: String, lei: String? = null) = Provider(
        "P9", name, RegisterField(lei, Provenance(ProvenanceLabel.Planned, now)), RegisterField(null, Provenance(ProvenanceLabel.Planned, now)),
    )

    @Test
    fun `an exact legal-name match suggests the identity columns and the parent`() = runTest {
        val directory = FakeDirectory(listOf(stripe), mapOf(stripe.lei to "549300PARENT00000US1"))
        val out = LeiLookup(directory).lookup(provider("Stripe Payments Europe Ltd"), now)
        val byColumn = out.suggestions.associate { it.column!! to it.value }
        assertEquals(
            mapOf(
                "B_05.01.0010" to stripe.lei, "B_05.01.0020" to "LEI", "B_05.01.0050" to stripe.legalName,
                "B_05.01.0080" to "IE", "B_05.01.0110" to "549300PARENT00000US1", "B_05.01.0120" to "LEI",
            ),
            byColumn,
        )
        assertTrue(out.suggestions.all { it.kind == SuggestionKind.RoiValue && it.resourceId == "P9" && it.citations.isNotEmpty() })
        assertEquals(6, out.result.added)
    }

    @Test
    fun `two records with the same name is insufficient evidence`() = runTest {
        val out = LeiLookup(FakeDirectory(listOf(stripe, stripe.copy(lei = "549300STRIPE0000IE99")))).lookup(provider(stripe.legalName), now)
        assertTrue(out.suggestions.isEmpty())
        assertTrue("insufficient evidence" in out.result.message)
    }

    @Test
    fun `a known LEI is looked up directly and values already right are not suggested again`() = runTest {
        val p = provider("Stripe", stripe.lei).copy(headquartersCountry = RegisterField("IE", Provenance(ProvenanceLabel.HumanConfirmed, now)))
        val out = LeiLookup(FakeDirectory(listOf(stripe))).lookup(p, now)
        assertEquals(setOf("B_05.01.0020", "B_05.01.0050"), out.suggestions.map { it.column }.toSet())
        assertEquals(1.0, out.suggestions.first().confidence)
    }

    @Test
    fun `gleif json api bodies become records`() {
        val body = """{"data":[{"type":"lei-records","id":"549300STRIPE0000IE12","attributes":{"lei":"549300STRIPE0000IE12",
            "entity":{"legalName":{"name":"Stripe Payments Europe, Limited","language":"en"},"status":"ACTIVE",
            "headquartersAddress":{"country":"IE","city":"Dublin"}}}}]}"""
        assertEquals(listOf(stripe), GleifJson.records(body))
        val single = """{"data":{"type":"lei-records","attributes":{"lei":"X1","entity":{"legalName":{"name":"X"},"status":"ACTIVE"}}}}"""
        assertEquals("X1", GleifJson.records(single).single().lei)
    }

    @Test
    fun `the route adds suggestions and needs a reviewer`() = testApplication {
        val p1 = LeiRecord("MSFT005R2W8D6N1C4H70", "Microsoft Ireland Operations Limited", "IE", "ACTIVE")
        application { module(fixtureServices().copy(leiDirectory = FakeDirectory(listOf(p1)))) }
        DoraiApi("http://localhost", client).use { api ->
            val result = assertIs<ApiResult.Success<dorai.shared.model.LeiLookupResult>>(api.leiLookup("P1")).value
            assertTrue(result.added > 0, result.message)
            val pending = assertIs<ApiResult.Success<List<dorai.shared.model.Suggestion>>>(api.suggestions()).value
            assertTrue(pending.any { it.kind == SuggestionKind.RoiValue && it.column == "B_05.01.0050" })
        }
        assertEquals(Role.Reviewer, Access.required(HttpMethod.Post, "/api/register/providers/P1/lei-lookup"))
    }
}
