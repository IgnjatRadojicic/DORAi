package dorai.shared.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** A cloud resource, scanned live or planned by Terraform. */
@Serializable
data class Resource(
    val id: String,
    val name: String,
    val type: String,
    val provider: String,
    val location: String,
    val country: String? = null,
    val source: ResourceSource,
    val changeAction: ChangeAction,
    val tags: Map<String, String> = emptyMap(),
    val serviceType: ServiceType? = null,
    val redundancy: Redundancy,
    val previousRedundancy: Redundancy? = null,
    val functionId: String? = null,
    val contractId: String? = null,
    val monthlyCost: Double? = null,
    val currency: String? = null,
)

@Serializable
data class ScanResult(
    val mode: ScanMode,
    val scannedAt: Instant,
    val resourceCount: Int,
    val newSuggestionCount: Int,
    val costSource: CostSource = CostSource.Fixture,
)
