package dorai.shared.rules

import dorai.shared.model.BusinessFunction
import dorai.shared.model.ChangeAction
import dorai.shared.model.Contract
import dorai.shared.model.ContractService
import dorai.shared.model.FinancialEntity
import dorai.shared.model.Provenance
import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.Provider
import dorai.shared.model.Redundancy
import dorai.shared.model.Register
import dorai.shared.model.RegisterField
import dorai.shared.model.Resource
import dorai.shared.model.ResourceSource
import dorai.shared.model.ServiceType
import dorai.shared.model.Severity
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class DiffRulesTest {

    private val at = Instant.parse("2026-01-01T00:00:00Z")
    private fun field(value: String) = RegisterField(value, Provenance(ProvenanceLabel.HumanConfirmed, at))

    private val register = Register(
        entity = FinancialEntity("5299000J2N45DDNE4Y28", "Test Pay", "DE"),
        providers = listOf(Provider("P1", "Microsoft Ireland Operations Ltd", field("549300"), field("IE"))),
        contracts = listOf(
            Contract(
                id = "C1",
                providerId = "P1",
                startDate = LocalDate(2025, 1, 1),
                annualCost = field("1000"),
                services = listOf(ContractService("C1-S1", field("S17"), field("F1"), field("DE"), emptyList())),
            ),
        ),
        functions = listOf(BusinessFunction("F1", "Payments", isCritical = true), BusinessFunction("F5", "Analytics", isCritical = false)),
        asOf = at,
    )

    private fun resource(
        location: String = "germanywestcentral",
        action: ChangeAction = ChangeAction.Create,
        provider: String = "Azure",
        function: String? = "F1",
        contract: String? = "C1",
        type: ServiceType? = ServiceType.S17,
        redundancy: Redundancy = Redundancy.Unknown,
        previous: Redundancy? = null,
        id: String = "azurerm_storage_account.st",
    ) = Resource(
        id = id, name = "st", type = "azurerm_storage_account", provider = provider, location = location,
        source = ResourceSource.Planned, changeAction = action,
        tags = listOfNotNull(function?.let { FUNCTION_TAG to it }, contract?.let { CONTRACT_TAG to it }).toMap(),
        serviceType = type, redundancy = redundancy, previousRedundancy = previous, functionId = function, contractId = contract,
    )

    private fun diff(vararg r: Resource) = RegisterDiff().diff(register, r.toList())
    private fun rules(vararg r: Resource) = diff(*r).map { it.ruleId to it.severity }

    @Test
    fun aRegisteredChangeInTheDeclaredCountryIsClean() = assertEquals(emptyList(), rules(resource()))

    @Test
    fun unchangedResourcesAreIgnored() = assertEquals(emptyList(), rules(resource(location = "eastus", action = ChangeAction.None)))

    @Test
    fun unknownCloudIsANewProvider() {
        val f = diff(resource(provider = "google")).single { it.ruleId == "new-provider" }
        assertEquals(Severity.High, f.severity)
        assertEquals(listOf("B_05.01", "B_02.01"), f.templates)
    }

    @Test
    fun outsideTheEuIsHigh() {
        // No service type, so the new-service-type rule stays out of this test.
        val f = diff(resource(location = "eastus", function = "F5", type = null)).single()
        assertEquals("data-location" to Severity.High, f.ruleId to f.severity)
        assertEquals("eastus (US)", f.location)
    }

    @Test
    fun unknownLocationIsMedium() {
        val f = diff(resource(location = "")).single()
        assertEquals("data-location" to Severity.Medium, f.ruleId to f.severity)
        assertEquals("unknown", f.location)
    }

    @Test
    fun anotherEuCountryThanDeclaredIsHigh() {
        val f = diff(resource(location = "westeurope")).single()
        assertEquals("Data location differs from register", f.title)
        assertTrue("declares DE for C1" in f.detail)
    }

    @Test
    fun criticalFunctionOutsideTheEu() {
        val findings = diff(resource(location = "eastus"))
        assertEquals(listOf("critical-function", "data-location"), findings.map { it.ruleId })
        assertTrue(findings.all { it.severity == Severity.High })
        assertEquals("Payments", findings.first().functionName)
        assertTrue(findings.first().criticalFunction)
    }

    @Test
    fun criticalFunctionDeleted() {
        val f = diff(resource(action = ChangeAction.Delete)).single()
        assertEquals("critical-function", f.ruleId)
        assertTrue(f.detail.endsWith("is deleted."))
    }

    @Test
    fun criticalFunctionLosesGeoRedundancy() {
        val f = diff(resource(action = ChangeAction.Update, redundancy = Redundancy.Local, previous = Redundancy.Geo)).single()
        assertTrue("loses its multi-region redundancy" in f.detail)
    }

    @Test
    fun nonCriticalDeleteIsClean() = assertEquals(emptyList(), rules(resource(action = ChangeAction.Delete, function = "F5")))

    @Test
    fun missingTagsAreMedium() {
        val f = diff(resource(function = null, contract = null)).single()
        assertEquals("untagged" to Severity.Medium, f.ruleId to f.severity)
        assertTrue("no dora-function and dora-contract tag" in f.detail)
        assertEquals(emptyList(), rules(resource(function = null, contract = null, action = ChangeAction.Delete)))
    }

    @Test
    fun newServiceTypeOnlyForCreatesWithoutARow() {
        assertEquals(listOf("new-service-type" to Severity.Medium), rules(resource(type = ServiceType.S18)))
        assertEquals(emptyList(), rules(resource(type = ServiceType.S18, action = ChangeAction.Update)))
        assertEquals(emptyList(), rules(resource(type = null)))
    }

    @Test
    fun findingsAreOrderedHighFirstThenByResourceAndRule() {
        val findings = diff(
            resource(id = "b", function = null, contract = null),
            resource(id = "a", location = "eastus"),
        )
        assertEquals(
            listOf("a" to "critical-function", "a" to "data-location", "b" to "untagged"),
            findings.map { it.resourceAddress to it.ruleId },
        )
    }
}
