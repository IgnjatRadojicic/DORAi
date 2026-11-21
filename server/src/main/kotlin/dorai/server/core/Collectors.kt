package dorai.server.core

import dorai.shared.model.CostSource
import dorai.shared.model.Resource
import dorai.shared.model.ScanMode
import kotlinx.datetime.LocalDate

/** Reads the resources of one cloud (Azure, AWS) or its fixture stand-in. */
interface CloudCollector {
    /** Provider name as it appears on resources, for example "Azure". */
    val provider: String

    suspend fun collect(): CollectResult
}

data class CollectResult(val resources: List<Resource>, val mode: ScanMode)

/** Monthly cost per resource for a date range. */
interface CostProvider {
    suspend fun costs(from: LocalDate, to: LocalDate): CostResult
}

data class CostResult(val entries: List<CostEntry>, val source: CostSource)

data class CostEntry(
    val resourceId: String,
    val provider: String,
    /** First day of the month the amount belongs to. */
    val month: LocalDate,
    val amount: Double,
    val currency: String,
)
