package dorai.server.register

import dorai.server.auth.Access
import dorai.server.fixtures.FixtureData
import dorai.server.fixtures.InMemoryRegisterRepository
import dorai.server.module
import dorai.shared.api.ApiResult
import dorai.shared.api.DoraiApi
import dorai.shared.model.Citation
import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.Role
import dorai.shared.model.RoiRow
import io.ktor.http.HttpMethod
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Register edits and the RoI view through the real server and the shared client. */
class RegisterEditTest {

    private fun api(block: suspend (DoraiApi) -> Unit) = testApplication {
        application { module() }
        DoraiApi("http://localhost", client).use { block(it) }
    }

    private fun <T> ApiResult<T>.ok(): T = when (this) {
        is ApiResult.Success -> value
        is ApiResult.Failure -> fail("Expected success, got $problem")
    }

    private fun ApiResult<*>.status(): Int = assertIs<ApiResult.Failure>(this).problem.status

    @Test
    fun `roi view has all 15 templates and lists what is missing`() = api { api ->
        val view = api.roi(LocalDate(2026, 12, 31)).ok()
        assertEquals(15, view.tables.size)
        assertEquals(LocalDate(2026, 12, 31), view.referenceDate)
        assertTrue(view.tables.getValue("B_02.02").isNotEmpty())
        assertTrue(view.issues.any { it.code == "roi-missing" })
        assertEquals(view.errorCount == 0, view.ready)
    }

    @Test
    fun `a changed value becomes human-confirmed, an unchanged one keeps its provenance`() = api { api ->
        val before = api.register().ok().providers.first()
        val sent = before.copy(headquartersCountry = before.headquartersCountry.copy(value = "NL"))
        val saved = api.putProvider(sent).ok()
        assertEquals("NL", saved.headquartersCountry.value)
        assertEquals("Set by development", saved.headquartersCountry.provenance.reason)
        assertEquals(before.lei, saved.lei, "LEI was not changed, so its provenance stays")
    }

    @Test
    fun `a client cannot claim a value was scanned`() = api { api ->
        val c = api.register().ok().contracts.first()
        val forged = c.annualCost.copy(value = "1", provenance = c.annualCost.provenance.copy(label = ProvenanceLabel.Scanned))
        val saved = api.putContract(c.copy(annualCost = forged)).ok()
        assertEquals(ProvenanceLabel.HumanConfirmed, saved.annualCost.provenance.label)
    }

    @Test
    fun `contract columns show up in the roi view`() = api { api ->
        val c = api.register().ok().contracts.first()
        val notice = c.annualCost.copy(value = "90")
        api.putContract(c.copy(roi = c.roi + ("B_02.02.0100" to notice))).ok()
        val rows = api.roi().ok().tables.getValue("B_02.02").filter { it.value("B_02.02.0010") == c.id }
        assertTrue(rows.isNotEmpty() && rows.all { it.value("B_02.02.0100") == "90" })
    }

    @Test
    fun `unknown columns, mismatched ids and providers in use are refused`() = api { api ->
        val r = api.register().ok()
        val p = r.providers.first()
        assertEquals(400, api.putProvider(p.copy(roi = mapOf("B_05.01.9999" to p.lei))).status())
        assertEquals(409, api.deleteProvider(p.id).status())
        assertEquals(404, api.deleteFunction("F99").status())
        assertEquals(400, api.putRow("B_05.02", RoiRow("x", mapOf("B_02.02.0010" to p.lei))).status())
    }

    @Test
    fun `template rows can be added and removed`() = api { api ->
        val field = api.register().ok().providers.first().lei
        api.putRow("B_01.03", RoiRow("BR1", mapOf("B_01.03.0030" to field.copy(value = "FinPay Milan")))).ok()
        assertEquals("FinPay Milan", api.roi().ok().tables.getValue("B_01.03").single().value("B_01.03.0030"))
        api.deleteRow("B_01.03", "BR1").ok()
        assertTrue(api.roi().ok().tables.getValue("B_01.03").isEmpty())
    }

    @Test
    fun `an accepted AI value is written as the reviewer's value with its citation`() = runBlocking {
        val data = FixtureData.load()
        val repo = InMemoryRegisterRepository(data.register, data.resources, data.costs)
        val service = data.register.contracts.first().services.first()
        val citation = Citation("cir-annex-i-b0202-0170", "CIR (EU) 2024/2956", "Annex I", "B_02.02.0170", "Sensitiveness of the data stored by the provider", 0.9)
        RegisterEditor(repo).acceptValue(service.id, "B_02.02.0170", "3", citation, "Payment data", "rita")
        val stored = repo.register().contracts.first().services.first().roi.getValue("B_02.02.0170")
        assertEquals("3", stored.value)
        assertEquals(ProvenanceLabel.HumanConfirmed, stored.provenance.label)
        assertEquals(citation, stored.provenance.citation)
    }

    @Test
    fun `one cell can be set, shown as the person's value, and cleared`() = api { api ->
        val view = api.roi(LocalDate(2026, 12, 31)).ok()
        val row = view.tables.getValue("B_02.02").first()
        val item = row.key.substringBefore('#')
        api.putValue(item, "B_02.02.0180", "4").ok()
        val set = api.roi(LocalDate(2026, 12, 31)).ok().tables.getValue("B_02.02").first { it.key == row.key }.cells.getValue("B_02.02.0180")
        assertEquals("4" to ProvenanceLabel.HumanConfirmed, set.value to set.label)
        api.putValue(item, "B_02.02.0180", " ").ok()
        assertNull(api.roi(LocalDate(2026, 12, 31)).ok().tables.getValue("B_02.02").first { it.key == row.key }.cells["B_02.02.0180"])
        api.putEntityValue("B_01.02.0040", "1").ok()
        assertEquals("1", api.register().ok().entity.roi.getValue("B_01.02.0040").value)
        assertEquals(400, api.putValue(item, "B_09.99.0010", "x").status())
        assertEquals(403, api.putValue(api.register().ok().entity.lei, "B_01.02.0040", "2").status())
    }

    @Test
    fun `edits need a reviewer, entity settings and definitions an admin`() {
        assertEquals(Role.Reviewer, Access.required(HttpMethod.Put, "/api/register/contracts/C1"))
        assertEquals(Role.Reviewer, Access.required(HttpMethod.Delete, "/api/register/rows/B_05.02/x"))
        assertEquals(Role.Admin, Access.required(HttpMethod.Put, "/api/register/entity"))
        assertEquals(Role.Admin, Access.required(HttpMethod.Put, "/api/register/entity/values/B_01.02.0040"))
        assertEquals(Role.Reviewer, Access.required(HttpMethod.Put, "/api/register/values/C1-S1/B_02.02.0180"))
        assertEquals(Role.Admin, Access.required(HttpMethod.Put, "/api/register/rows/B_99.01/r1"))
        assertEquals(Role.Viewer, Access.required(HttpMethod.Get, "/api/roi"))
        assertNull(Access.required(HttpMethod.Get, "/api/health"))
    }
}
