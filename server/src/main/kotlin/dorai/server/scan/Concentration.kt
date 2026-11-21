package dorai.server.scan

import dorai.server.fixtures.CostSeed
import dorai.server.fixtures.round2
import dorai.shared.model.BusinessFunction
import dorai.shared.model.ConcentrationReport
import dorai.shared.model.ConcentrationShare
import dorai.shared.model.Resource
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.roundToLong

const val UNASSIGNED = "Unassigned"

/** Splits total cost by provider, region and business function. Shares are 0..1, rounded to 4 decimals. */
fun concentration(
    resources: List<Resource>,
    costs: List<CostSeed>,
    functions: List<BusinessFunction>,
): ConcentrationReport {
    val critical = functions.filter { it.isCritical }.map { it.id }.toSet()
    val costByResource = costs.groupBy { it.resourceId }.mapValues { (_, c) -> c.sumOf { it.amount } }
    val total = costByResource.values.sum()
    val months = costs.map { it.month }

    fun shares(key: (Resource) -> String): List<ConcentrationShare> =
        resources.groupBy(key).map { (k, group) ->
            val cost = group.sumOf { costByResource[it.id] ?: 0.0 }
            ConcentrationShare(
                key = k,
                cost = round2(cost),
                share = if (total == 0.0) 0.0 else (cost / total * 10_000).roundToLong() / 10_000.0,
                resourceCount = group.size,
                criticalFunction = group.any { it.functionId in critical },
            )
        }.sortedByDescending { it.cost }

    return ConcentrationReport(
        from = months.minOrNull() ?: LocalDate(1970, 1, 1),
        to = months.maxOrNull()?.plus(1, DateTimeUnit.MONTH)?.minus(1, DateTimeUnit.DAY) ?: LocalDate(1970, 1, 1),
        totalCost = round2(total),
        currency = costs.firstOrNull()?.currency ?: "EUR",
        byProvider = shares { it.provider },
        byRegion = shares { it.location },
        byFunction = shares { it.functionId ?: UNASSIGNED },
    )
}
