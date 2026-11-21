package dorai.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * RoI columns an item has beyond its named fields, keyed by the CIR column code (for example `B_02.02.0100`).
 * Values are text like every register field; the readiness check parses dates, numbers and codes.
 * A column with several values (countries in B_02.02.0150 and 0160) holds them comma separated.
 */
typealias RoiFields = Map<String, RegisterField>

/** The DORA Register of Information of one financial entity, or of a group with the reporting entity first. */
@Serializable
data class Register(
    val entity: FinancialEntity,
    val providers: List<Provider>,
    val contracts: List<Contract>,
    val functions: List<BusinessFunction>,
    val asOf: Instant,
    /**
     * Rows of templates that have no item of their own, by template code: other group entities (B_01.02),
     * branches (B_01.03), intra-group links (B_02.03, B_03.03), subcontractors at rank 2 or more (B_05.02)
     * and the entity's own definitions (B_99.01).
     */
    val rows: Map<String, List<RoiRow>> = emptyMap(),
)

/** One row of a template kept as columns by code, for example a branch or a subcontractor link. */
@Serializable
data class RoiRow(val id: String, val fields: RoiFields = emptyMap())

/** The entity maintaining the register (B_01.01, and its own row in B_01.02). */
@Serializable
data class FinancialEntity(
    val lei: String,
    val name: String,
    val country: String,
    val roi: RoiFields = emptyMap(),
)

/** An ICT third-party service provider (B_05.01), direct or a subcontractor. */
@Serializable
data class Provider(
    val id: String,
    val name: String,
    val lei: RegisterField,
    val headquartersCountry: RegisterField,
    val roi: RoiFields = emptyMap(),
)

/** A contractual arrangement (B_02.01) and the services it covers (B_02.02, one row per service). */
@Serializable
data class Contract(
    val id: String,
    val providerId: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val annualCost: RegisterField,
    val currency: String? = null,
    val services: List<ContractService>,
    val roi: RoiFields = emptyMap(),
)

/**
 * One ICT service of a contract. Its [roi] holds the B_02.02 columns that differ per service and the
 * assessment of the service (B_07.01.0050 to 0120) when it supports a critical or important function.
 */
@Serializable
data class ContractService(
    val id: String,
    val serviceType: RegisterField,
    val functionId: RegisterField,
    val dataLocation: RegisterField,
    val resourceIds: List<String>,
    val roi: RoiFields = emptyMap(),
)

/** A function of the entity (B_06.01). Function ids follow the CIR pattern: F followed by a number. */
@Serializable
data class BusinessFunction(
    val id: String,
    val name: String,
    val isCritical: Boolean,
    val roi: RoiFields = emptyMap(),
)
