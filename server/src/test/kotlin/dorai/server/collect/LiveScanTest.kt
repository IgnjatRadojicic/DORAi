package dorai.server.collect

import dorai.server.collect.LiveConfig.Cloud
import dorai.server.collect.aws.AwsArns
import dorai.server.collect.azure.AzureCostRows
import dorai.server.collect.azure.AzureRows
import dorai.server.core.CostEntry
import dorai.server.core.CostProvider
import dorai.server.core.CostResult
import dorai.shared.model.CostSource
import dorai.shared.model.Redundancy
import dorai.shared.model.ServiceType
import dorai.server.core.CloudCollector
import dorai.server.core.CollectResult
import dorai.server.fixtures.fixtureServices
import dorai.server.module
import dorai.server.scan.concentration
import dorai.shared.model.DoraiJson
import dorai.shared.model.Problem
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveConfigTest {

    @Test
    fun fixturesUnlessLive() {
        assertNull(LiveConfig.fromEnv(emptyMap()))
        assertNull(LiveConfig.fromEnv(mapOf("DORAI_DATA_SOURCE" to "fixtures")))
        assertFailsWith<IllegalStateException> { LiveConfig.fromEnv(mapOf("DORAI_DATA_SOURCE" to "prod")) }
    }

    @Test
    fun defaults() {
        val c = LiveConfig.fromEnv(mapOf("DORAI_DATA_SOURCE" to "live"))!!
        assertEquals(setOf(Cloud.Azure, Cloud.Aws), c.clouds)
        assertEquals(listOf("eu-central-1"), c.awsRegions)
        assertEquals("EUR", c.costCurrency)
        assertNull(c.awsEndpoint)
    }

    @Test
    fun everySetting() {
        val c = LiveConfig.fromEnv(
            mapOf(
                "DORAI_DATA_SOURCE" to "LIVE", "DORAI_CLOUDS" to "aws", "DORAI_AZURE_SUBSCRIPTIONS" to "a, b",
                "DORAI_AZURE_RESOURCE_GROUP" to "rg-finpay-prod", "AWS_REGION" to "eu-west-1",
                "DORAI_AWS_ENDPOINT" to "http://localhost:4566", "DORAI_COST_CURRENCY" to "eur", "DORAI_FX_USD" to "0.92",
            ),
        )!!
        assertEquals(setOf(Cloud.Aws), c.clouds)
        assertEquals(listOf("a", "b"), c.azureSubscriptions)
        assertEquals("rg-finpay-prod", c.azureResourceGroup)
        assertEquals(listOf("eu-west-1"), c.awsRegions)
        assertEquals("http://localhost:4566", c.awsEndpoint)
        assertEquals(mapOf("USD" to 0.92), c.exchangeRates)
    }

    @Test
    fun unsafeValuesAreRejected() {
        val live = mapOf("DORAI_DATA_SOURCE" to "live")
        // The resource group ends up in a Resource Graph query.
        assertFailsWith<IllegalArgumentException> { LiveConfig.fromEnv(live + ("DORAI_AZURE_RESOURCE_GROUP" to "x' or 1==1 //")) }
        assertFailsWith<IllegalArgumentException> { LiveConfig.fromEnv(live + ("DORAI_FX_USD" to "abc")) }
        assertFailsWith<IllegalStateException> { LiveConfig.fromEnv(live + ("DORAI_CLOUDS" to "gcp")) }
    }
}

class AzureRowsTest {

    private val storage = mapOf(
        "id" to "/subscriptions/S/resourceGroups/RG/providers/Microsoft.Storage/storageAccounts/stFinPay",
        "name" to "stFinPay", "type" to "Microsoft.Storage/storageAccounts", "location" to "germanywestcentral",
        "tags" to mapOf("Dora-Function" to "F1", "dora-contract" to "C1", "count" to 3),
        "sku" to mapOf("name" to "Standard_RAGRS"),
    )

    @Test
    fun aRowBecomesAResource() {
        val r = AzureRows.toResource(storage)
        assertEquals("/subscriptions/s/resourcegroups/rg/providers/microsoft.storage/storageaccounts/stfinpay", r.id)
        assertEquals("stFinPay", r.name)
        assertEquals("microsoft.storage/storageaccounts", r.type)
        assertEquals(ServiceType.S17, r.serviceType)
        assertEquals("DE", r.country)
        assertEquals(Redundancy.Geo, r.redundancy)
        assertEquals("F1" to "C1", r.functionId to r.contractId)
        assertEquals(mapOf("Dora-Function" to "F1", "dora-contract" to "C1"), r.tags)
    }

    @Test
    fun missingFieldsAndPostgresRedundancy() {
        val bare = AzureRows.toResource(mapOf("type" to "Microsoft.KeyVault/vaults", "tags" to null))
        assertEquals("" to Redundancy.Unknown, bare.location to bare.redundancy)
        val pg = mapOf("type" to "Microsoft.DBforPostgreSQL/flexibleServers")
        assertEquals(Redundancy.Local, AzureRows.toResource(pg).redundancy)
        assertEquals(Redundancy.Zone, AzureRows.toResource(pg + ("ha" to "ZoneRedundant")).redundancy)
        assertEquals(Redundancy.Geo, AzureRows.toResource(pg + ("geoBackup" to "Enabled")).redundancy)
    }

    @Test
    fun replicasAndBackupsBecomeCountriesAndBackupInfo() {
        val storage = mapOf("type" to "Microsoft.Storage/storageAccounts", "location" to "westeurope", "secondaryLocation" to "northeurope")
        assertEquals(listOf("IE"), AzureRows.toResource(storage).replicaCountries)
        val cosmos = mapOf(
            "type" to "Microsoft.DocumentDB/databaseAccounts", "location" to "westeurope",
            "replicaLocations" to listOf(mapOf("locationName" to "West Europe"), mapOf("locationName" to "germanywestcentral")),
        )
        assertEquals(listOf("westeurope", "germanywestcentral").size, AzureRows.replicaRegions(cosmos).size)
        val pg = mapOf("type" to "Microsoft.DBforPostgreSQL/flexibleServers", "backupDays" to 14, "geoBackup" to "Enabled")
        val b = AzureRows.toResource(pg).backup!!
        assertEquals(Triple(1, 14, true), Triple(b.intervalHours, b.retentionDays, b.geoRedundant))
        assertEquals(null, AzureRows.toResource(storage).backup)
    }

    @Test
    fun sellersOnTheBillBecomeVendorSpend() {
        val azure = AzureCostRows.parseVendors(
            listOf("Cost", "BillingMonth", "PublisherName", "Currency"),
            listOf(listOf(100.0, "2026-10-01T00:00:00", "Datadog", "EUR"), listOf(50.0, "2026-11-01T00:00:00", "Datadog", "EUR")),
        )
        val aws = AwsArns.sellers(listOf(
            AwsArns.SellerCost("Amazon Web Services EMEA SARL", LocalDate(2026, 10, 1), 900.0, "USD"),
            AwsArns.SellerCost("Elastic N.V.", LocalDate(2026, 10, 1), 40.0, "USD"),
        ))
        assertEquals(listOf("vendor:Elastic N.V."), aws.map { it.resourceId })
        val spend = dorai.server.core.Vendors.spend(azure + aws + CostEntry("/x/vm", "Azure", LocalDate(2026, 10, 1), 7.0, "EUR"))
        assertEquals(setOf("Datadog" to 150.0, "Elastic N.V." to 40.0), spend.map { it.seller to it.amount }.toSet())
    }

    @Test
    fun resourceExplorerAddsOnlyResourcesTheTaggingApiMissed() {
        val tagged = listOf(AwsArns.toResource("arn:aws:s3:::finpay-statements", mapOf("dora-function" to "F3"), "eu-central-1"))
        val all = AwsArns.withUntagged(tagged, listOf(
            "arn:aws:s3:::finpay-statements" to "eu-central-1",
            "arn:aws:s3:::finpay-logs" to "eu-west-1",
            "arn:aws:iam::1:role/x" to "global",
        ))
        assertEquals(3, all.size)
        assertEquals("F3", all.first().functionId)
        val logs = all.single { it.id == "arn:aws:s3:::finpay-logs" }
        assertEquals("eu-west-1" to null, logs.location to logs.functionId)
    }

    @Test
    fun securityToolFindingsAttachToResourcesByLowercasedId() {
        val defender = AzureRows.alerts(listOf(
            mapOf("alertId" to "/a/1", "resourceId" to "/subscriptions/s/resourcegroups/rg/providers/x/y", "title" to "Enable MFA", "severity" to "High"),
            mapOf("alertId" to "/a/2", "resourceId" to "", "title" to "No resource"),
        ))
        assertEquals(listOf("HIGH"), defender.getValue("/subscriptions/s/resourcegroups/rg/providers/x/y").map { it.severity })
        assertEquals(1, defender.size)
        val hub = AwsArns.alertsByResource(listOf(AwsArns.HubFinding("f1", "S3 public", "CRITICAL", listOf("arn:aws:s3:::Bucket", "arn:aws:iam::1:role/r"))))
        assertEquals(setOf("arn:aws:s3:::bucket", "arn:aws:iam::1:role/r"), hub.keys)
        assertEquals("Security Hub", hub.values.first().single().source)
    }

    @Test
    fun theQueryNarrowsToTheResourceGroupOnlyWhenSet() {
        assertTrue("resourceGroup" !in AzureRows.query(null))
        assertTrue("| where resourceGroup =~ 'rg-finpay-prod'" in AzureRows.query("rg-finpay-prod"))
    }

    @Test
    fun costRowsAreReadByColumnName() {
        val rows = AzureCostRows.parse(
            listOf("ResourceId", "Currency", "Cost", "BillingMonth"),
            listOf(
                listOf("/subscriptions/S/RG/X", "USD", 12.345, "2026-10-01T00:00:00"),
                listOf("/subscriptions/S/RG/Y", "", "7", "2026-11-01T00:00:00"),
                listOf("", "USD", 1.0, "2026-11-01T00:00:00"),
                listOf("/subscriptions/S/RG/Z", "USD", "n/a", "2026-11-01T00:00:00"),
            ),
        )
        assertEquals(
            listOf(
                CostEntry("/subscriptions/s/rg/x", "Azure", LocalDate(2026, 10, 1), 12.35, "USD"),
                CostEntry("/subscriptions/s/rg/y", "Azure", LocalDate(2026, 11, 1), 7.0, "EUR"),
            ),
            rows,
        )
        assertEquals(emptyList(), AzureCostRows.parse(listOf("Cost"), listOf(listOf(1.0))))
    }

    @Test
    fun usageDateNumbersAreMonths() {
        assertEquals(LocalDate(2026, 11, 1), AzureCostRows.monthOf(20261115))
        assertEquals(LocalDate(2026, 11, 1), AzureCostRows.monthOf(2.0261115E7))
        assertNull(AzureCostRows.monthOf("garbage"))
    }
}

class AwsArnsTest {

    @Test
    fun typesUseTheFixtureSpelling() {
        assertEquals("s3:bucket", AwsArns.typeOf("arn:aws:s3:::finpay-statements"))
        assertEquals("dynamodb:table", AwsArns.typeOf("arn:aws:dynamodb:eu-central-1:000000000000:table/finpay-statements-index"))
        assertEquals("lambda:function", AwsArns.typeOf("arn:aws:lambda:eu-central-1:000000000000:function:finpay-statement-renderer"))
        assertEquals("sqs:queue", AwsArns.typeOf("arn:aws:sqs:eu-central-1:000000000000:finpay-statement-jobs"))
        assertEquals("rds:db", AwsArns.typeOf("arn:aws:rds:us-east-1:1:db:payments-replica"))
        assertEquals("ec2:instance", AwsArns.typeOf("arn:aws:ec2:eu-central-1:1:instance/i-0abc"))
        assertEquals("", AwsArns.typeOf("not-an-arn"))
    }

    @Test
    fun aTaggedArnBecomesAResource() {
        val r = AwsArns.toResource("arn:aws:s3:::finpay-statements", mapOf("dora-function" to "F3"), "eu-central-1")
        assertEquals("finpay-statements", r.name)
        assertEquals("eu-central-1" to "DE", r.location to r.country)
        assertEquals(ServiceType.S17, r.serviceType)
        assertEquals("F3", r.functionId)
        assertEquals("us-east-1", AwsArns.toResource("arn:aws:rds:us-east-1:1:db:x", emptyMap(), "eu-central-1").location)
    }

    @Test
    fun dailyCostIsSummedPerResourceAndMonth() {
        val d = { id: String, day: Int, amount: Double -> AwsArns.DailyCost(id, LocalDate(2026, 11, day), amount, "USD") }
        val monthly = AwsArns.monthly(listOf(d("finpay-statements", 1, 0.104), d("finpay-statements", 2, 0.2), d("arn:aws:rds:x", 2, 0.0), d("", 3, 5.0)))
        assertEquals(listOf(CostEntry("arn:aws:s3:::finpay-statements", "AWS", LocalDate(2026, 11, 1), 0.3, "USD")), monthly)
    }
}

class LiveCostsTest {

    private val nov = LocalDate(2026, 11, 1)
    private fun entry(currency: String, amount: Double = 10.0) = CostEntry("r", "Azure", nov, amount, currency)

    @Test
    fun convertsWithFixedRatesAndLeavesOutUnknownCurrencies() {
        val converter = CurrencyConverter("EUR", mapOf("USD" to 0.92))
        assertEquals(listOf(entry("EUR"), entry("EUR", 9.2)), converter.convert(listOf(entry("eur"), entry("USD"), entry("GBP"))))
    }

    private fun costs(block: () -> CostResult) = object : CostProvider {
        override suspend fun costs(from: LocalDate, to: LocalDate) = block()
    }

    private fun provider(source: CostSource, vararg e: CostEntry) = costs { CostResult(e.toList(), source) }

    @Test
    fun sourceAndFailures() = runTest {
        val converter = CurrencyConverter("EUR", emptyMap())
        val failing = costs { error("429 Too Many Requests") }
        fun combined(vararg p: CostProvider) =
            CombinedCostProvider(p.mapIndexed { i, it -> CombinedCostProvider.Part("c$i", it) }, converter)

        val mixed = combined(provider(CostSource.Live, entry("EUR")), provider(CostSource.Fixture, entry("EUR")), failing).costs(nov, nov)
        assertEquals(CostSource.Mixed to 2, mixed.source to mixed.entries.size)
        assertEquals(CostSource.Live, combined(provider(CostSource.Live, entry("EUR")), provider(CostSource.Fixture)).costs(nov, nov).source)
        assertEquals(CostSource.Live to 0, combined(failing).costs(nov, nov).let { it.source to it.entries.size })
    }
}

class ScanFailureTest {

    @Test
    fun aFailingCloudFailsTheScanAndKeepsTheStoredOne() = testApplication {
        val base = fixtureServices()
        val failing = object : CloudCollector {
            override val provider = "Azure"
            override suspend fun collect(): CollectResult = error("AADSTS700016: application not found")
        }
        application { module(base.copy(collectors = base.collectors + failing)) }
        val before = base.register.resources()

        val response = client.post("/api/scan")
        assertEquals(HttpStatusCode.BadGateway, response.status)
        val problem = DoraiJson.decodeFromString<Problem>(response.bodyAsText())
        assertEquals("Azure scan failed", problem.title)
        assertTrue("AADSTS700016" in problem.detail.orEmpty())
        assertEquals(before, base.register.resources())
    }
}

class ConcentrationTest {

    @Test
    fun costOfResourcesNotInTheScanIsLeftOut() {
        val nov = LocalDate(2026, 11, 1)
        val r = cloudResource("/subscriptions/s/x", "x", "microsoft.storage/storageaccounts", "Azure", "westeurope", emptyMap())
        val report = concentration(
            listOf(r),
            listOf(CostEntry("/SUBSCRIPTIONS/S/X", "Azure", nov, 10.0, "EUR"), CostEntry("/subscriptions/s/deleted", "Azure", nov, 90.0, "EUR")),
            emptyList(),
        )
        assertEquals(10.0, report.totalCost)
        assertEquals(1.0, report.byProvider.single().share)
    }
}
