package dorai.server.fixtures

import dorai.shared.model.BusinessFunction
import dorai.shared.model.ChangeAction
import dorai.shared.model.Citation
import dorai.shared.model.Contract
import dorai.shared.model.ContractService
import dorai.shared.model.DoraiJson
import dorai.shared.model.FinancialEntity
import dorai.shared.model.Finding
import dorai.shared.model.Provenance
import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.Provider
import dorai.shared.model.Redundancy
import dorai.shared.model.Register
import dorai.shared.model.RegisterField
import dorai.shared.model.Resource
import dorai.shared.model.ResourceSource
import dorai.shared.model.Suggestion
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.roundToLong
import kotlin.time.Instant

/**
 * The FinPay demo data in `resources/fixtures`. FinPay is a fictional payment firm.
 * Scan and register files use a seed format; this class maps them to the API types.
 */
class FixtureData(
    val scan: ScanSeed,
    val register: Register,
    val planEastus: ByteArray,
    val findingsEastus: List<Finding>,
    val suggestions: List<Suggestion>,
) {
    val resources: List<Resource> = scan.resources.map { it.toResource(scan.costs) }

    companion object {
        fun load(): FixtureData = FixtureData(
            scan = DoraiJson.decodeFromString(text("finpay-scan.json")),
            register = DoraiJson.decodeFromString<RegisterSeed>(text("finpay-register.json")).toRegister(),
            planEastus = bytes("plan-eastus.json"),
            findingsEastus = DoraiJson.decodeFromString(text("findings-eastus.json")),
            suggestions = DoraiJson.decodeFromString(text("suggestions.json")),
        )

        private fun bytes(name: String): ByteArray =
            FixtureData::class.java.getResourceAsStream("/fixtures/$name")?.use { it.readBytes() }
                ?: error("Fixture missing: /fixtures/$name")

        private fun text(name: String) = bytes(name).decodeToString()
    }
}

// Seed format of finpay-scan.json

@Serializable
data class ScanSeed(
    val scannedAt: Instant,
    val costSource: String,
    val resources: List<ResourceSeed>,
    val costs: List<CostSeed>,
)

@Serializable
data class ResourceSeed(
    val id: String,
    val name: String,
    val type: String,
    val provider: String,
    val location: String,
    val source: ResourceSource,
    val changeAction: ChangeAction = ChangeAction.None,
    val tags: Map<String, String> = emptyMap(),
    val redundancy: Redundancy = Redundancy.Unknown,
)

@Serializable
data class CostSeed(
    val resourceId: String,
    val provider: String,
    val month: LocalDate,
    val amount: Double,
    val currency: String,
)

private fun ResourceSeed.toResource(costs: List<CostSeed>): Resource {
    val own = costs.filter { it.resourceId == id }
    return Resource(
        id = id,
        name = name,
        type = type,
        provider = provider,
        location = location,
        source = source,
        changeAction = changeAction,
        tags = tags,
        redundancy = redundancy,
        functionId = tags.entries.firstOrNull { it.key.equals("dora-function", ignoreCase = true) }?.value,
        contractId = tags.entries.firstOrNull { it.key.equals("dora-contract", ignoreCase = true) }?.value,
        monthlyCost = if (own.isEmpty()) null else round2(own.sumOf { it.amount } / own.map { it.month }.distinct().size),
        currency = own.firstOrNull()?.currency,
    )
}

internal fun round2(value: Double): Double = (value * 100).roundToLong() / 100.0

// Seed format of finpay-register.json: values may be numbers, labels are written "HumanConfirmed"

@Serializable
private data class RegisterSeed(
    val entity: FinancialEntity,
    val providers: List<ProviderSeed>,
    val contracts: List<ContractSeed>,
    val functions: List<BusinessFunction>,
    val asOf: Instant,
)

@Serializable
private data class ProviderSeed(val id: String, val name: String, val lei: FieldSeed, val headquartersCountry: FieldSeed)

@Serializable
private data class ContractSeed(
    val id: String,
    val providerId: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val annualCost: FieldSeed,
    val currency: String? = null,
    val services: List<ServiceSeed>,
)

@Serializable
private data class ServiceSeed(
    val id: String,
    val serviceType: FieldSeed,
    val functionId: FieldSeed,
    val dataLocation: FieldSeed,
    val resourceIds: List<String> = emptyList(),
)

@Serializable
private data class FieldSeed(val value: JsonPrimitive? = null, val provenance: ProvenanceSeed)

@Serializable
private data class ProvenanceSeed(
    val label: String,
    val updatedAt: Instant,
    val citation: Citation? = null,
    val reason: String? = null,
)

private fun RegisterSeed.toRegister() = Register(
    entity = entity,
    providers = providers.map { Provider(it.id, it.name, it.lei.toField(), it.headquartersCountry.toField()) },
    contracts = contracts.map { c ->
        Contract(
            id = c.id,
            providerId = c.providerId,
            startDate = c.startDate,
            endDate = c.endDate,
            annualCost = c.annualCost.toField(),
            currency = c.currency,
            services = c.services.map {
                ContractService(it.id, it.serviceType.toField(), it.functionId.toField(), it.dataLocation.toField(), it.resourceIds)
            },
        )
    },
    functions = functions,
    asOf = asOf,
)

private fun FieldSeed.toField() = RegisterField(
    value = value?.let { p ->
        if (p.isString) p.content
        else p.doubleOrNull?.let { d -> if (d % 1.0 == 0.0) d.toLong().toString() else d.toString() } ?: p.content
    },
    provenance = Provenance(provenance.label.toLabel(), provenance.updatedAt, provenance.citation, provenance.reason),
)

private fun String.toLabel(): ProvenanceLabel {
    val key = replace("-", "").lowercase()
    return ProvenanceLabel.entries.firstOrNull { it.name.lowercase() == key }
        ?: error("Unknown provenance label in fixture: $this")
}
