package dorai.server.collect.azure

import com.azure.core.credential.TokenCredential
import com.azure.core.management.AzureEnvironment
import com.azure.core.management.profile.AzureProfile
import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.resourcemanager.costmanagement.CostManagementManager
import com.azure.resourcemanager.costmanagement.models.ExportType
import com.azure.resourcemanager.costmanagement.models.FunctionType
import com.azure.resourcemanager.costmanagement.models.GranularityType
import com.azure.resourcemanager.costmanagement.models.QueryAggregation
import com.azure.resourcemanager.costmanagement.models.QueryColumnType
import com.azure.resourcemanager.costmanagement.models.QueryDataset
import com.azure.resourcemanager.costmanagement.models.QueryDefinition
import com.azure.resourcemanager.costmanagement.models.QueryGrouping
import com.azure.resourcemanager.costmanagement.models.QueryTimePeriod
import com.azure.resourcemanager.costmanagement.models.TimeframeType
import com.azure.resourcemanager.resourcegraph.ResourceGraphManager
import com.azure.resourcemanager.resourcegraph.models.QueryRequest
import com.azure.resourcemanager.resourcegraph.models.QueryRequestOptions
import com.azure.resourcemanager.resourcegraph.models.ResultFormat
import dorai.server.collect.LiveConfig
import dorai.server.core.CloudCollector
import dorai.server.core.CollectResult
import dorai.server.core.CostProvider
import dorai.server.core.CostResult
import dorai.shared.model.CostSource
import dorai.shared.model.ScanMode
import dorai.shared.rules.ServiceTypeTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Azure access through the standard credential chain: AZURE_* service principal variables in Docker,
 * managed identity on Azure, or the developer's `az login`. Reader is enough for the scan,
 * Cost Management Reader for cost.
 */
class AzureClients(private val config: LiveConfig) {
    private val credential: TokenCredential by lazy { DefaultAzureCredentialBuilder().build() }
    private val profile by lazy { AzureProfile(AzureEnvironment.AZURE) }
    val graph: ResourceGraphManager by lazy { ResourceGraphManager.authenticate(credential, profile) }
    val cost: CostManagementManager by lazy { CostManagementManager.authenticate(credential, profile) }

    /** Runs a Resource Graph query over the configured (or all readable) subscriptions, following skip tokens. */
    fun query(kql: String): List<Map<String, Any?>> {
        val rows = mutableListOf<Map<String, Any?>>()
        var skipToken: String? = null
        do {
            val request = QueryRequest()
                .withQuery(kql)
                .withOptions(QueryRequestOptions().withResultFormat(ResultFormat.OBJECT_ARRAY).withSkipToken(skipToken))
            if (config.azureSubscriptions.isNotEmpty()) request.withSubscriptions(config.azureSubscriptions)
            val response = graph.resourceProviders().resources(request)
            @Suppress("UNCHECKED_CAST")
            rows += (response.data() as? List<Map<String, Any?>>).orEmpty()
            skipToken = response.skipToken()
        } while (!skipToken.isNullOrEmpty())
        return rows
    }
}

class AzureCollector(private val clients: AzureClients, private val config: LiveConfig) : CloudCollector {
    override val provider = ServiceTypeTable.AZURE

    override suspend fun collect() = withContext(Dispatchers.IO) {
        CollectResult(clients.query(AzureRows.query(config.azureResourceGroup)).map(AzureRows::toResource), ScanMode.Live)
    }
}

/**
 * Actual cost per resource per month from Cost Management, one query per subscription (or resource group).
 * Billing data lags a day or two and some offers return nothing; that just means no Azure cost.
 */
class AzureCostProvider(private val clients: AzureClients, private val config: LiveConfig) : CostProvider {

    override suspend fun costs(from: LocalDate, to: LocalDate) = withContext(Dispatchers.IO) {
        // Cost Management refuses periods longer than one year.
        val start = maxOf(from, to.minus(DatePeriod(years = 1)).plus(DatePeriod(days = 1)))
        val subscriptions = config.azureSubscriptions.ifEmpty {
            clients.query(AzureRows.SUBSCRIPTIONS).mapNotNull { it["subscriptionId"] as? String }
        }
        val entries = subscriptions.flatMap { subscription ->
            val scope = "/subscriptions/$subscription" + (config.azureResourceGroup?.let { "/resourceGroups/$it" } ?: "")
            val result = clients.cost.queries().usage(scope, definition(start, to))
            AzureCostRows.parse(result.columns().map { it.name() }, result.rows())
        }
        CostResult(entries, CostSource.Live)
    }

    private fun definition(from: LocalDate, to: LocalDate) = QueryDefinition()
        .withType(ExportType.ACTUAL_COST)
        .withTimeframe(TimeframeType.CUSTOM)
        .withTimePeriod(
            QueryTimePeriod()
                .withFrom(OffsetDateTime.of(from.year, from.month.ordinal + 1, from.day, 0, 0, 0, 0, ZoneOffset.UTC))
                .withTo(OffsetDateTime.of(to.year, to.month.ordinal + 1, to.day, 23, 59, 59, 0, ZoneOffset.UTC)),
        )
        .withDataset(
            QueryDataset()
                .withGranularity(GranularityType.fromString("Monthly"))
                .withAggregation(mapOf("totalCost" to QueryAggregation().withName("Cost").withFunction(FunctionType.SUM)))
                .withGrouping(listOf(QueryGrouping().withType(QueryColumnType.DIMENSION).withName("ResourceId"))),
        )
}
