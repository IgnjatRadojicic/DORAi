package dorai.server.core

import dorai.shared.roi.VendorSpend

/**
 * Seller spend travels with the other cost rows, under the id `vendor:<seller>`. No resource has such an id,
 * so per-resource cost and concentration never count it; the RoI checks read it back with [vendorSpend].
 */
object Vendors {
    const val PREFIX = "vendor:"

    fun id(seller: String) = PREFIX + seller.trim()

    fun spend(costs: List<CostEntry>): List<VendorSpend> =
        costs.filter { it.resourceId.startsWith(PREFIX) }
            .groupBy { Triple(it.resourceId.removePrefix(PREFIX), it.provider, it.currency) }
            .map { (k, rows) -> VendorSpend(k.first, k.second, rows.sumOf { it.amount }, k.third) }
}
