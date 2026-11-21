package dorai.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** The DORA Register of Information of one financial entity. */
@Serializable
data class Register(
    val entity: FinancialEntity,
    val providers: List<Provider>,
    val contracts: List<Contract>,
    val functions: List<BusinessFunction>,
    val asOf: Instant,
)

@Serializable
data class FinancialEntity(val lei: String, val name: String, val country: String)

@Serializable
data class Provider(
    val id: String,
    val name: String,
    val lei: RegisterField,
    val headquartersCountry: RegisterField,
)

@Serializable
data class Contract(
    val id: String,
    val providerId: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val annualCost: RegisterField,
    val currency: String? = null,
    val services: List<ContractService>,
)

@Serializable
data class ContractService(
    val id: String,
    val serviceType: RegisterField,
    val functionId: RegisterField,
    val dataLocation: RegisterField,
    val resourceIds: List<String>,
)

@Serializable
data class BusinessFunction(val id: String, val name: String, val isCritical: Boolean)
