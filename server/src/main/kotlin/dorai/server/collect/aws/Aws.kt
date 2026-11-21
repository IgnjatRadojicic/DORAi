package dorai.server.collect.aws

import aws.sdk.kotlin.services.costexplorer.CostExplorerClient
import aws.sdk.kotlin.services.costexplorer.getCostAndUsageWithResources
import aws.sdk.kotlin.services.costexplorer.model.DateInterval
import aws.sdk.kotlin.services.costexplorer.model.Dimension
import aws.sdk.kotlin.services.costexplorer.model.DimensionValues
import aws.sdk.kotlin.services.costexplorer.model.Expression
import aws.sdk.kotlin.services.costexplorer.model.Granularity
import aws.sdk.kotlin.services.costexplorer.model.GroupDefinition
import aws.sdk.kotlin.services.costexplorer.model.GroupDefinitionType
import aws.sdk.kotlin.services.resourcegroupstaggingapi.ResourceGroupsTaggingClient
import aws.sdk.kotlin.services.resourcegroupstaggingapi.paginators.getResourcesPaginated
import dorai.server.collect.LiveConfig
import dorai.server.core.CloudCollector
import dorai.server.core.CollectResult
import dorai.server.core.CostProvider
import dorai.server.core.CostResult
import dorai.shared.model.CostSource
import dorai.shared.model.Resource
import dorai.shared.model.ScanMode
import dorai.shared.rules.ServiceTypeTable
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * Real AWS: the Resource Groups Tagging API, one call chain per configured region. Credentials come from the
 * standard chain (AWS_* variables, profile, role). Limit: resources that were never tagged are not returned.
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
        return CollectResult(resources.distinctBy(Resource::id), ScanMode.Live)
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
        return CostResult(AwsArns.monthly(daily), CostSource.Live)
    }

    private companion object {
        const val METRIC = "UnblendedCost"
    }
}
