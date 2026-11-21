package dorai.server.collect.aws

import dorai.server.collect.cloudResource
import dorai.server.collect.round2
import dorai.server.core.CostEntry
import dorai.server.core.Vendors
import dorai.shared.model.Resource
import dorai.shared.rules.ServiceTypeTable
import kotlinx.datetime.LocalDate

/** ARNs to resources and Cost Explorer groups to cost entries. Pure, so it is tested without AWS. */
object AwsArns {

    /**
     * The type in the fixture spelling `service:resource-type` (`s3:bucket`, `dynamodb:table`, `rds:db`), so the
     * Annex III table and stored rows match a fixture scan. ARNs without a resource type get the service's
     * usual one (S3 buckets, SQS queues, SNS topics).
     */
    fun typeOf(arn: String): String {
        val parts = arn.split(':', limit = 6)
        if (parts.size < 6) return ""
        val service = parts[2]
        val resource = parts[5]
        val type = when {
            '/' in resource && (':' !in resource || resource.indexOf('/') < resource.indexOf(':')) -> resource.substringBefore('/')
            ':' in resource -> resource.substringBefore(':')
            else -> DEFAULT_TYPE[service] ?: service
        }
        return "$service:$type"
    }

    /** The resource's own name: the part after the last `/` or `:` (bucket name for S3). */
    fun nameOf(arn: String): String = arn.substringAfterLast(':').substringAfterLast('/')

    /** Region from the ARN; S3 bucket ARNs have none, so the region that was queried is used. */
    fun regionOf(arn: String, queried: String): String = arn.split(':').getOrNull(3)?.ifEmpty { null } ?: queried

    fun toResource(arn: String, tags: Map<String, String>, queriedRegion: String): Resource =
        cloudResource(arn, nameOf(arn), typeOf(arn), ServiceTypeTable.AWS, regionOf(arn, queriedRegion), tags)

    /**
     * Cost Explorer reports S3 buckets by name and most other resources by ARN. Bucket names become
     * bucket ARNs so they join with the scanned resources.
     */
    fun costResourceId(id: String): String =
        if (id.startsWith("arn:") || id.startsWith("i-") || id.isBlank()) id else "arn:aws:s3:::$id"

    /** Daily cost rows summed per resource and month. */
    fun monthly(daily: List<DailyCost>): List<CostEntry> =
        daily.filter { it.amount != 0.0 && it.resourceId.isNotBlank() }
            .groupBy { Triple(costResourceId(it.resourceId), LocalDate(it.day.year, it.day.month, 1), it.currency) }
            .map { (key, rows) -> CostEntry(key.first, ServiceTypeTable.AWS, key.second, round2(rows.sumOf { it.amount }), key.third) }

    data class DailyCost(val resourceId: String, val day: LocalDate, val amount: Double, val currency: String)

    data class SellerCost(val seller: String, val month: LocalDate, val amount: Double, val currency: String)

    /** Seller rows to vendor cost entries; AWS's own billing entities are the cloud itself, not a vendor. */
    fun sellers(rows: List<SellerCost>): List<CostEntry> =
        rows.filter { it.amount != 0.0 && it.seller.isNotBlank() && !isAws(it.seller) }
            .groupBy { Triple(it.seller.trim(), LocalDate(it.month.year, it.month.month, 1), it.currency) }
            .map { (k, r) -> CostEntry(Vendors.id(k.first), ServiceTypeTable.AWS, k.second, round2(r.sumOf { it.amount }), k.third) }

    private fun isAws(seller: String) =
        seller.trim().let { it.startsWith("Amazon", ignoreCase = true) || it.startsWith("AWS", ignoreCase = true) }

    private val DEFAULT_TYPE = mapOf("s3" to "bucket", "sqs" to "queue", "sns" to "topic")
}
