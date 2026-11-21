package dorai.server.collect

import dorai.server.collect.LiveConfig.Cloud
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
    return copy(collectors = collectors, costs = CombinedCostProvider(costs, CurrencyConverter(config.costCurrency, config.exchangeRates)))
}
