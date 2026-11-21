package dorai.server.collect

import dorai.server.core.CostEntry
import dorai.shared.model.BackupInfo
import dorai.shared.model.ChangeAction
import dorai.shared.model.Redundancy
import dorai.shared.model.Resource
import dorai.shared.model.ResourceSource
import dorai.shared.rules.CONTRACT_TAG
import dorai.shared.rules.FUNCTION_TAG
import dorai.shared.rules.Regions
import dorai.shared.rules.ServiceTypeTable
import kotlin.math.roundToLong

/**
 * A scanned resource with everything derived the same way for every source: country from the region,
 * Annex III code from the type table, function and contract from the dora-* tags (any case).
 */
fun cloudResource(
    id: String,
    name: String,
    type: String,
    provider: String,
    location: String,
    tags: Map<String, String>,
    redundancy: Redundancy = Redundancy.Unknown,
    source: ResourceSource = ResourceSource.Live,
    changeAction: ChangeAction = ChangeAction.None,
    replicaRegions: List<String> = emptyList(),
    backup: BackupInfo? = null,
) = Resource(
    id = id,
    name = name,
    type = type,
    provider = provider,
    location = location,
    country = Regions.countryOf(location),
    serviceType = ServiceTypeTable.find(type)?.code,
    source = source,
    changeAction = changeAction,
    tags = tags,
    redundancy = redundancy,
    functionId = tags.tag(FUNCTION_TAG),
    contractId = tags.tag(CONTRACT_TAG),
    replicaCountries = replicaRegions.mapNotNull { Regions.countryOf(it) }.filter { it != Regions.countryOf(location) }.distinct(),
    backup = backup,
)

private fun Map<String, String>.tag(key: String) = entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value

/** Average monthly cost per resource over the months that have cost, rounded to cents. Ids match ignoring case. */
fun List<Resource>.withMonthlyCost(costs: List<CostEntry>): List<Resource> {
    val byResource = costs.groupBy { it.resourceId.lowercase() }
    return map { r ->
        val own = byResource[r.id.lowercase()].orEmpty()
        if (own.isEmpty()) r
        else r.copy(
            monthlyCost = round2(own.sumOf { it.amount } / own.map { it.month }.distinct().size),
            currency = own.first().currency,
        )
    }
}

fun round2(value: Double): Double = (value * 100).roundToLong() / 100.0
