package dorai.shared.rules

import dorai.shared.model.BusinessFunction
import dorai.shared.model.Contract
import dorai.shared.model.ContractService
import dorai.shared.model.FinancialEntity
import dorai.shared.model.Provenance
import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.Provider
import dorai.shared.model.Register
import dorai.shared.model.RegisterField
import dorai.shared.model.ValidationIssue
import dorai.shared.model.ValidationSeverity
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class RegisterChecksTest {

    private val at = Instant.parse("2026-01-01T00:00:00Z")
    private fun field(value: String?) = RegisterField(value, Provenance(ProvenanceLabel.HumanConfirmed, at))

    private val entityLei = leiFrom("TEST00FINPAY000001")
    private val p1Lei = leiFrom("TEST00PROVIDER0001")
    private val p2Lei = leiFrom("TEST00PROVIDER0002")

    private fun service(id: String = "C1-S1", type: String? = "S17", function: String? = "F1", location: String? = "DE") =
        ContractService(id, field(type), field(function), field(location), emptyList())

    private fun contract(
        id: String = "C1",
        provider: String = "P1",
        end: LocalDate? = null,
        cost: String? = "1000",
        currency: String? = "EUR",
        services: List<ContractService> = listOf(service()),
    ) = Contract(id, provider, LocalDate(2025, 1, 1), end, field(cost), currency, services)

    private val valid = Register(
        entity = FinancialEntity(entityLei, "Test Pay", "DE"),
        providers = listOf(
            Provider("P1", "Microsoft Ireland Operations Ltd", field(p1Lei), field("IE")),
            Provider("P2", "Amazon Web Services EMEA SARL", field(p2Lei), field("LU")),
        ),
        contracts = listOf(contract()),
        functions = listOf(BusinessFunction("F1", "Payments", isCritical = true)),
        asOf = at,
    )

    private fun check(register: Register) = RulesRegisterValidator().validate(register)

    private fun single(register: Register): ValidationIssue {
        val report = check(register)
        assertEquals(1, report.issues.size, report.issues.toString())
        return report.issues.single()
    }

    private fun ValidationIssue.short() = listOf(severity.name, code, template, field, entityId)

    @Test
    fun aCompleteRegisterHasNoIssues() {
        val report = check(valid)
        assertTrue(report.isValid)
        assertEquals(emptyList(), report.issues)
        assertEquals(0 to 0, report.errorCount to report.warningCount)
    }

    @Test
    fun entityMandatoryFields() {
        assertEquals(listOf("Error", "mandatory-field", "B_01.01", "Name", entityLei), single(valid.copy(entity = valid.entity.copy(name = " "))).short())
        assertEquals(listOf("Error", "mandatory-field", "B_01.01", "Country", entityLei), single(valid.copy(entity = valid.entity.copy(country = ""))).short())
        // Without an LEI the entity has no id to report.
        assertEquals(listOf("Error", "mandatory-field", "B_01.01", "Lei", null), single(valid.copy(entity = valid.entity.copy(lei = ""))).short())
    }

    @Test
    fun providerMandatoryFields() {
        val noName = valid.copy(providers = listOf(valid.providers[0].copy(name = ""), valid.providers[1]))
        assertEquals(listOf("Error", "mandatory-field", "B_05.01", "Name", "P1"), single(noName).short())
        val noCountry = valid.copy(providers = listOf(valid.providers[0], valid.providers[1].copy(headquartersCountry = field(null))))
        assertEquals(listOf("Error", "mandatory-field", "B_05.01", "HeadquartersCountry", "P2"), single(noCountry).short())
    }

    @Test
    fun invalidLeis() {
        val bad = entityLei.dropLast(1) + if (entityLei.last() == '9') '0' else entityLei.last() + 1
        assertEquals(listOf("Error", "invalid-lei", "B_01.01", "Lei", bad), single(valid.copy(entity = valid.entity.copy(lei = bad))).short())
        val badProvider = valid.copy(providers = listOf(valid.providers[0], valid.providers[1].copy(lei = field("549300"))))
        assertEquals(listOf("Error", "invalid-lei", "B_05.01", "Lei", "P2"), single(badProvider).short())
    }

    @Test
    fun aMissingProviderLeiIsOnlyAWarning() {
        val register = valid.copy(providers = listOf(valid.providers[0].copy(lei = field(null)), valid.providers[1]))
        val report = check(register)
        assertTrue(report.isValid)
        assertEquals(0 to 1, report.errorCount to report.warningCount)
        assertEquals(listOf("Warning", "missing-lei", "B_05.01", "Lei", "P1"), report.issues.single().short())
    }

    @Test
    fun duplicateIds() {
        val register = valid.copy(
            providers = valid.providers + valid.providers[0],
            functions = valid.functions + valid.functions[0],
            contracts = listOf(contract(services = listOf(service(), service()))),
        )
        assertEquals(
            listOf("B_05.01" to "P1", "B_06.01" to "F1", "B_02.02" to "C1-S1"),
            check(register).issues.map { assertEquals("duplicate-key", it.code); it.template to it.entityId },
        )
    }

    @Test
    fun contractFields() {
        assertEquals(listOf("Error", "mandatory-field", "B_02.01", "Currency", "C1"), single(valid.copy(contracts = listOf(contract(currency = null)))).short())
        assertEquals(listOf("Error", "mandatory-field", "B_02.01", "AnnualCost", "C1"), single(valid.copy(contracts = listOf(contract(cost = null)))).short())
        assertEquals(
            listOf("Error", "invalid-date-range", "B_02.02", "EndDate", "C1"),
            single(valid.copy(contracts = listOf(contract(end = LocalDate(2024, 12, 31))))).short(),
        )
        assertEquals(emptyList(), check(valid.copy(contracts = listOf(contract(end = LocalDate(2025, 1, 1))))).issues)
    }

    @Test
    fun brokenReferences() {
        val f = single(valid.copy(contracts = listOf(contract(provider = "P9"))))
        assertEquals(listOf("Error", "unknown-provider", "B_02.01", "ProviderId", "C1"), f.short())
        assertEquals("Provider 'P9' does not exist in the register.", f.message)
        assertEquals(
            listOf("Error", "unknown-function", "B_02.02", "FunctionId", "C1-S1"),
            single(valid.copy(contracts = listOf(contract(services = listOf(service(function = "F9")))))).short(),
        )
        assertEquals(
            listOf("Error", "mandatory-field", "B_02.02", "FunctionId", "C1-S1"),
            single(valid.copy(contracts = listOf(contract(services = listOf(service(function = null)))))).short(),
        )
    }

    @Test
    fun serviceTypes() {
        fun typed(t: String?) = single(valid.copy(contracts = listOf(contract(services = listOf(service(type = t)))))).short()
        assertEquals(listOf("Error", "mandatory-field", "B_02.02", "ServiceType", "C1-S1"), typed(null))
        assertEquals(listOf("Error", "mandatory-field", "B_02.02", "ServiceType", "C1-S1"), typed("Unknown"))
        assertEquals(listOf("Error", "invalid-service-type", "B_02.02", "ServiceType", "C1-S1"), typed("S20"))
    }

    @Test
    fun aMissingDataLocationIsAWarning() {
        val report = check(valid.copy(contracts = listOf(contract(services = listOf(service(location = ""))))))
        assertTrue(report.isValid)
        assertEquals(listOf("Warning", "missing-data-location", "B_02.02", "DataLocation", "C1-S1"), report.issues.single().short())
    }

    @Test
    fun everyIssueIsCollected() {
        val register = valid.copy(
            entity = valid.entity.copy(name = ""),
            providers = listOf(valid.providers[0].copy(lei = field("BAD")), valid.providers[1].copy(lei = field(null))),
            contracts = listOf(contract(currency = null)),
        )
        val report = check(register)
        assertFalse(report.isValid)
        assertEquals(3 to 1, report.errorCount to report.warningCount)
        assertEquals(listOf("Name", "Lei", "Lei", "Currency"), report.issues.map { it.field })
        assertEquals(ValidationSeverity.Warning, report.issues[2].severity)
    }
}
