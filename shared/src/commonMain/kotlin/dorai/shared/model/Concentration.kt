package dorai.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** How ICT cost is spread over providers, regions and business functions. */
@Serializable
data class ConcentrationReport(
    val from: LocalDate,
    val to: LocalDate,
    val totalCost: Double,
    val currency: String,
    val byProvider: List<ConcentrationShare>,
    val byRegion: List<ConcentrationShare>,
    val byFunction: List<ConcentrationShare>,
)

@Serializable
data class ConcentrationShare(
    val key: String,
    val cost: Double,
    val share: Double,
    val resourceCount: Int,
    val criticalFunction: Boolean,
)
