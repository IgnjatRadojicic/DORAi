package dorai.server.collect

import dorai.server.collect.LiveConfig.Cloud
import dorai.server.collect.apps.EntraApps
import dorai.server.collect.apps.OktaApps
import dorai.server.collect.apps.SaasAppsCollector
import dorai.server.collect.aws.AwsCostExplorerProvider
import dorai.server.collect.aws.AwsTaggingCollector
import dorai.server.collect.aws.LocalStackCollector
import dorai.server.collect.azure.AzureClients
import dorai.server.collect.azure.AzureCollector
import dorai.server.collect.azure.AzureCostProvider
import dorai.server.core.CloudCollector
import dorai.server.core.CostEntry
import dorai.server.core.Services
import dorai.server.fixtures.FixtureCostProvider
import dorai.shared.rules.ServiceTypeTable
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("dorai.server.collect")

/**
 * Live scan wiring on top of [base]: real collectors and cost for the configured clouds; everything else
 * (register storage, rules, AI) stays as it is. [fixtureCosts] supply AWS cost on LocalStack, which has no billing.
 */
fun Services.withLiveScan(config: LiveConfig, fixtureCosts: List<CostEntry>): Services {
    val collectors = mutableListOf<CloudCollector>()
    val costs = mutableListOf<CombinedCostProvider.Part>()

    if (Cloud.Azure in config.clouds) {
        val azure = AzureClients(config)
        collectors += AzureCollector(azure, config)
        costs += CombinedCostProvider.Part(ServiceTypeTable.AZURE, AzureCostProvider(azure, config))
    }
    if (Cloud.Aws in config.clouds) {
        if (config.awsEndpoint != null) {
            collectors += LocalStackCollector(config)
            costs += CombinedCostProvider.Part(
                ServiceTypeTable.AWS,
                FixtureCostProvider(fixtureCosts.filter { it.provider == ServiceTypeTable.AWS }),
            )
        } else {
            collectors += AwsTaggingCollector(config)
            costs += CombinedCostProvider.Part(ServiceTypeTable.AWS, AwsCostExplorerProvider())
        }
    }
    val apps = buildList {
        if (LiveConfig.App.Entra in config.apps) add(EntraApps(System.getenv("AZURE_TENANT_ID")))
        if (LiveConfig.App.Okta in config.apps) {
            val token = System.getenv("DORAI_OKTA_TOKEN")
            if (token.isNullOrBlank()) log.warn("DORAI_APPS has okta but DORAI_OKTA_TOKEN is not set: Okta apps are skipped.")
            else add(OktaApps(config.oktaUrl!!, token))
        }
    }
    if (apps.isNotEmpty()) collectors += SaasAppsCollector(apps)
    return copy(collectors = collectors, costs = CombinedCostProvider(costs, CurrencyConverter(config.costCurrency, config.exchangeRates)))
}
