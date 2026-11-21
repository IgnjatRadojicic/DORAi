package dorai.server.collect

import dorai.server.collect.aws.AwsTaggingCollector
import dorai.server.collect.azure.AzureClients
import dorai.server.collect.azure.AzureCollector
import dorai.server.collect.azure.AzureCostProvider
import dorai.shared.model.ScanMode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Against the real clouds with the developer's own login, read-only. Skipped unless DORAI_LIVE_TESTS=1, so CI
 * and normal builds stay offline. Cost Explorer is left out on purpose: every call costs USD 0.01.
 */
class LiveSmokeTest {

    private val config = LiveConfig(clouds = LiveConfig.Cloud.entries.toSet(), awsRegions = listOf(System.getenv("AWS_REGION") ?: LiveConfig.DEFAULT_AWS_REGION))

    private fun live() = assumeTrue(System.getenv("DORAI_LIVE_TESTS") == "1", "Set DORAI_LIVE_TESTS=1 to run against the real clouds.")

    @Test
    fun azureScanAndCost() = runTest(timeout = 3.minutes) {
        live()
        val clients = AzureClients(config)
        val scan = AzureCollector(clients, config).collect()
        println("Azure: ${scan.resources.size} resources, ${scan.resources.count { it.serviceType != null }} with an Annex III code")
        assertEquals(ScanMode.Live, scan.mode)
        assertTrue(scan.resources.all { it.id == it.id.lowercase() && it.provider == "Azure" })

        val cost = AzureCostProvider(clients, config).costs(LocalDate(2025, 12, 1), LocalDate(2026, 11, 21))
        println("Azure cost: ${cost.entries.size} rows in ${cost.entries.map { it.currency }.toSet()}")
        assertTrue(cost.entries.all { it.month.day == 1 })
    }

    @Test
    fun awsTaggingScan() = runTest(timeout = 3.minutes) {
        live()
        val scan = AwsTaggingCollector(config).collect()
        println("AWS (${config.awsRegions}): ${scan.resources.size} tagged resources: ${scan.resources.map { it.type }.toSet()}")
        assertTrue(scan.resources.all { it.id.startsWith("arn:") && it.provider == "AWS" })
    }
}
