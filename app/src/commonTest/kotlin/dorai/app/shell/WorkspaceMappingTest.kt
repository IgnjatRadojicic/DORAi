package dorai.app.shell

import dorai.app.components.EvidenceCitation
import dorai.app.components.Provenance
import dorai.app.inventory.SourcedValue
import dorai.app.review.ReviewStatus
import dorai.app.review.SuggestionField
import dorai.shared.model.ChangeAction
import dorai.shared.model.Citation
import dorai.shared.model.CostSource
import dorai.shared.model.Redundancy
import dorai.shared.model.Resource
import dorai.shared.model.ResourceSource
import dorai.shared.model.ScanMode
import dorai.shared.model.ScanResult
import dorai.shared.model.ServiceType
import dorai.shared.model.Suggestion
import dorai.shared.model.SuggestionKind
import dorai.shared.model.SuggestionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Inventory and review values keep where they came from on the way to the screen. */
class WorkspaceMappingTest {
    private val functions = mapOf("F1" to "Payments", "F3" to "Fraud detection")
    private val citation = Citation("CIR-2024-2956/AnnexIII/S17", "CIR (EU) 2024/2956", "Annex III", "S17", "IaaS", 0.71)

    private fun resource(serviceType: ServiceType? = null, functionId: String? = null) = Resource(
        id = "/r/kv", name = "kv-finpay", type = "microsoft.keyvault/vaults", provider = "Azure",
        location = "germanywestcentral", country = "DE", source = ResourceSource.Live, changeAction = ChangeAction.None,
        serviceType = serviceType, redundancy = Redundancy.Unknown, functionId = functionId, monthlyCost = 0.02, currency = "EUR",
    )

    private fun suggestion(kind: SuggestionKind, value: String, status: SuggestionStatus, resourceId: String = "/r/kv") = Suggestion(
        id = "$kind-$value-$status", resourceId = resourceId, resourceName = "kv-finpay", kind = kind, value = value,
        reason = "Because.", citations = listOf(citation), confidence = 0.9, status = status, createdAt = Instant.fromEpochSeconds(0),
    )

    @Test
    fun scannedValuesAreScannedAndFunctionsCarryTheirName() {
        val mapped = resource(ServiceType.S17, "F1").toInventoryResource(emptyList(), functions)

        assertEquals(SourcedValue("S17", Provenance.SCANNED, "Cloud services: IaaS"), mapped.serviceType)
        assertEquals(SourcedValue("F1", Provenance.SCANNED, "Payments"), mapped.function)
    }

    @Test
    fun aScannedValueWinsOverAnySuggestion() {
        val suggestions = listOf(suggestion(SuggestionKind.ServiceType, "S18", SuggestionStatus.Accepted))
        assertEquals(Provenance.SCANNED, resource(ServiceType.S17).toInventoryResource(suggestions, functions).serviceType!!.provenance)
    }

    @Test
    fun withoutAScannedValueAcceptedIsHumanConfirmedAndPendingIsAiSuggested() {
        val suggestions = listOf(
            suggestion(SuggestionKind.ServiceType, "S18", SuggestionStatus.Pending),
            suggestion(SuggestionKind.BusinessFunction, "F3", SuggestionStatus.Accepted),
        )
        val mapped = resource().toInventoryResource(suggestions, functions)

        assertEquals(SourcedValue("S18", Provenance.AI_SUGGESTED, "Cloud services: PaaS"), mapped.serviceType)
        assertEquals(SourcedValue("F3", Provenance.HUMAN_CONFIRMED, "Fraud detection"), mapped.function)
    }

    @Test
    fun anAcceptedSuggestionWinsOverAPendingOne() {
        val suggestions = listOf(
            suggestion(SuggestionKind.ServiceType, "S18", SuggestionStatus.Pending),
            suggestion(SuggestionKind.ServiceType, "S17", SuggestionStatus.Accepted),
        )
        assertEquals(SourcedValue("S17", Provenance.HUMAN_CONFIRMED, "Cloud services: IaaS"), resource().toInventoryResource(suggestions, functions).serviceType)
    }

    @Test
    fun rejectedUnknownAndOtherResourcesLeaveTheValueEmpty() {
        val suggestions = listOf(
            suggestion(SuggestionKind.ServiceType, "S18", SuggestionStatus.Rejected),
            suggestion(SuggestionKind.BusinessFunction, "F1", SuggestionStatus.Accepted, resourceId = "/r/other"),
        )
        val mapped = resource(ServiceType.Unknown).toInventoryResource(suggestions, functions)

        assertNull(mapped.serviceType)
        assertNull(mapped.function)
    }

    @Test
    fun aSuggestionKeepsValueReasonCitationAndStatus() {
        val mapped = suggestion(SuggestionKind.BusinessFunction, "F1", SuggestionStatus.Rejected).toReviewSuggestion(functions)

        assertEquals(SuggestionField.BUSINESS_FUNCTION, mapped.field)
        assertEquals("F1", mapped.value)
        assertEquals("Payments", mapped.valueLabel)
        assertEquals("Because.", mapped.reason)
        assertEquals(listOf(EvidenceCitation("CIR-2024-2956/AnnexIII/S17", "CIR (EU) 2024/2956", "Annex III", "S17", "IaaS")), mapped.citations)
        assertEquals(ReviewStatus.REJECTED, mapped.status)
    }

    @Test
    fun aScanSummarySaysWhetherItWasLive() {
        val summary = ScanResult(ScanMode.Live, Instant.fromEpochSeconds(0), 15, 3, CostSource.Live).toScanSummary()
        assertTrue(summary.live && summary.liveCost)
        assertEquals(15, summary.resourceCount)
    }
}
