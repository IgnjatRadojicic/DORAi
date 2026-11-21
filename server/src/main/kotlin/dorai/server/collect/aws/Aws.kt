package dorai.server.collect.aws

import aws.sdk.kotlin.services.costexplorer.CostExplorerClient
import aws.sdk.kotlin.services.costexplorer.getCostAndUsage
import aws.sdk.kotlin.services.costexplorer.getCostAndUsageWithResources
import aws.sdk.kotlin.services.costexplorer.model.DateInterval
import aws.sdk.kotlin.services.costexplorer.model.Dimension
import aws.sdk.kotlin.services.costexplorer.model.DimensionValues
import aws.sdk.kotlin.services.costexplorer.model.Expression
import aws.sdk.kotlin.services.costexplorer.model.Granularity
import aws.sdk.kotlin.services.costexplorer.model.GroupDefinition
import aws.sdk.kotlin.services.costexplorer.model.GroupDefinitionType
import aws.sdk.kotlin.services.resourceexplorer2.ResourceExplorer2Client
import aws.sdk.kotlin.services.resourceexplorer2.paginators.listResourcesPaginated
import aws.sdk.kotlin.services.resourcegroupstaggingapi.ResourceGroupsTaggingClient
import aws.sdk.kotlin.services.resourcegroupstaggingapi.paginators.getResourcesPaginated
import dorai.server.collect.LiveConfig
import dorai.server.core.CloudCollector
import dorai.server.core.CollectResult
import dorai.server.core.CostEntry
import dorai.server.core.CostProvider
import dorai.server.core.CostResult
import dorai.shared.model.CostSource
import dorai.shared.model.Resource
import dorai.shared.model.ScanMode
import dorai.shared.rules.ServiceTypeTable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.slf4j.LoggerFactory

/**
 * Real AWS: the Resource Groups Tagging API, one call chain per configured region, plus Resource Explorer
 * for resources that were never tagged. Credentials come from the standard chain (AWS_* variables, profile,
 * role). Resource Explorer needs an index (and a default view) in the region; without one the scan keeps
 * the tagged resources only and logs why.
 */
class AwsTaggingCollector(private val config: LiveConfig) : CloudCollector {
    override val provider = ServiceTypeTable.AWS

    override suspend fun collect(): CollectResult {
        val resources = config.awsRegions.flatMap { region ->
            ResourceGroupsTaggingClient.fromEnvironment { this.region = region }.use { client ->
                client.getResourcesPaginated { resourcesPerPage = 100 }.toList()
                    .flatMap { it.resourceTagMappingList.orEmpty() }
                    .mapNotNull { m ->
                        val arn = m.resourceArn ?: return@mapNotNull null
                        AwsArns.toResource(arn, m.tags.orEmpty().associate { it.key to it.value }, region)
                    }
            }
        }
        val tagged = resources.distinctBy(Resource::id)
        return CollectResult(AwsArns.withUntagged(tagged, untagged()), ScanMode.Live)
    }

    /** ARN and region of every resource Resource Explorer knows, tagged or not. */
    private suspend fun untagged(): List<Pair<String, String>> = config.awsRegions.flatMap { region ->
        try {
            ResourceExplorer2Client.fromEnvironment { this.region = region }.use { client ->
                client.listResourcesPaginated { maxResults = 1000 }.toList()
                    .flatMap { it.resources.orEmpty() }
                    .mapNotNull { r -> r.arn?.let { it to (r.region ?: region) } }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Resource Explorer unavailable in {}, untagged resources are not scanned: {}", region, e.message)
            emptyList()
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(AwsTaggingCollector::class.java)
    }
}

/**
 * Real AWS cost per resource from Cost Explorer. AWS keeps resource-level data for 14 days only, and only
 * after "resource-level data" is turned on in the billing preferences, so this covers the last 14 days.
 * Each request costs USD 0.01.
 */
class AwsCostExplorerProvider : CostProvider {

    override suspend fun costs(from: LocalDate, to: LocalDate): CostResult {
        val start = maxOf(from, to.minus(DatePeriod(days = 14)))
        val daily = mutableListOf<AwsArns.DailyCost>()
        // Cost Explorer is a global service served from us-east-1.
        CostExplorerClient.fromEnvironment { region = "us-east-1" }.use { ce ->
            var token: String? = null
            do {
                val page = ce.getCostAndUsageWithResources {
                    timePeriod = DateInterval { this.start = start.toString(); end = to.toString() }
                    granularity = Granularity.Daily
                    metrics = listOf(METRIC)
                    groupBy = listOf(GroupDefinition { type = GroupDefinitionType.Dimension; key = "RESOURCE_ID" })
                    // This call requires a filter; usage rows are the ones that belong to resources.
                    filter = Expression { dimensions = DimensionValues { key = Dimension.RecordType; values = listOf("Usage") } }
                    nextPageToken = token
                }
                page.resultsByTime.orEmpty().forEach { day ->
                    val date = day.timePeriod?.start?.let(LocalDate::parse) ?: return@forEach
                    day.groups.orEmpty().forEach { g ->
                        val metric = g.metrics?.get(METRIC)
                        val amount = metric?.amount?.toDoubleOrNull() ?: return@forEach
                        daily += AwsArns.DailyCost(g.keys?.firstOrNull().orEmpty(), date, amount, metric.unit ?: "USD")
                    }
                }
                token = page.nextPageToken
            } while (!token.isNullOrEmpty())
        }
        return CostResult(AwsArns.monthly(daily) + sellers(from, to), CostSource.Live)
    }

    /**
     * Who the bill pays, by legal entity, for the whole period (Marketplace sellers bill under their own name).
     * Best effort: a refused call only means no seller check.
     */
    private suspend fun sellers(from: LocalDate, to: LocalDate): List<CostEntry> = try {
        val rows = mutableListOf<AwsArns.SellerCost>()
        CostExplorerClient.fromEnvironment { region = "us-east-1" }.use { ce ->
            var token: String? = null
            do {
                val page = ce.getCostAndUsage {
                    timePeriod = DateInterval { this.start = from.toString(); end = to.toString() }
                    granularity = Granularity.Monthly
                    metrics = listOf(METRIC)
                    groupBy = listOf(GroupDefinition { type = GroupDefinitionType.Dimension; key = "LEGAL_ENTITY_NAME" })
                    nextPageToken = token
                }
                page.resultsByTime.orEmpty().forEach { month ->
                    val start = month.timePeriod?.start?.let(LocalDate::parse) ?: return@forEach
                    month.groups.orEmpty().forEach { g ->
                        val metric = g.metrics?.get(METRIC)
                        val amount = metric?.amount?.toDoubleOrNull() ?: return@forEach
                        rows += AwsArns.SellerCost(g.keys?.firstOrNull().orEmpty(), start, amount, metric.unit ?: "USD")
                    }
                }
                token = page.nextPageToken
            } while (!token.isNullOrEmpty())
        }
        AwsArns.sellers(rows)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.warn("AWS seller spend unavailable: {}", e.message)
        emptyList()
    }

    private companion object {
        const val METRIC = "UnblendedCost"
        val log = LoggerFactory.getLogger(AwsCostExplorerProvider::class.java)
    }
}
