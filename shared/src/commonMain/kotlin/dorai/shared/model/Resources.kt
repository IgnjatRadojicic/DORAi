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
    /** Countries of copies in other regions (geo-redundant storage, database replicas), for B_02.02.0150. */
    val replicaCountries: List<String> = emptyList(),
    /** Backups the scan found, or null when it saw none or could not tell. */
    val backup: BackupInfo? = null,
)

/** Backups of one resource as the cloud reports them (DORA Art. 12). */
@Serializable
data class BackupInfo(
    /** Hours between backups (or the restore point interval); null when unknown. */
    val intervalHours: Int? = null,
    val retentionDays: Int? = null,
    /** A backup copy is kept in another region. */
    val geoRedundant: Boolean = false,
)

@Serializable
data class ScanResult(
    val mode: ScanMode,
    val scannedAt: Instant,
    val resourceCount: Int,
    val newSuggestionCount: Int,
    val costSource: CostSource = CostSource.Fixture,
)
