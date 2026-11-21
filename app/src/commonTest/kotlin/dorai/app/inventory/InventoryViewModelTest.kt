package dorai.app.inventory

import dorai.app.components.FrontendFailure
import dorai.app.components.Provenance
import dorai.app.components.runImmediate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InventoryViewModelTest {
    private fun resource(name: String) = InventoryResource(
        id = "/r/$name", name = name, type = "microsoft.storage/storageaccounts", provider = "Azure",
        location = "germanywestcentral", country = "DE", serviceType = SourcedValue("S17", Provenance.SCANNED),
        function = null, monthlyCost = 1.5, currency = "EUR",
    )

    private class FakeSource(var stored: List<InventoryResource>, var failScan: Boolean = false) : InventorySource {
        var scans = 0
        override suspend fun resources() = stored
        override suspend fun scan(): ScanSummary {
            if (failScan) throw FrontendFailure("Azure scan failed")
            scans++
            stored = stored + InventoryResourceFixture.scanned
            return ScanSummary(live = true, scannedAt = "2026-11-21T19:00:00Z", resourceCount = stored.size, newSuggestionCount = 2, liveCost = true)
        }
    }

    private object InventoryResourceFixture {
        val scanned = InventoryResource("/r/new", "new", "t", "Azure", "germanywestcentral", "DE", null, null, null, null)
    }

    @Test
    fun refreshLoadsTheStoredInventory() {
        val model = InventoryViewModel(FakeSource(listOf(resource("a"), resource("b"))))
        runImmediate { model.refresh() }

        assertEquals(listOf("a", "b"), model.state.resources!!.map { it.name })
        assertFalse(model.state.busy)
        assertNull(model.state.error)
    }

    @Test
    fun aScanShowsTheNewInventoryAndItsSummary() {
        val source = FakeSource(listOf(resource("a")))
        val model = InventoryViewModel(source)
        runImmediate { model.scan() }

        assertEquals(1, source.scans)
        assertEquals(2, model.state.resources!!.size)
        assertEquals(2, model.state.lastScan!!.newSuggestionCount)
        assertTrue(model.state.lastScan!!.live)
        assertFalse(model.state.scanning)
    }

    @Test
    fun aFailedScanKeepsTheStoredInventoryAndSaysSo() {
        val source = FakeSource(listOf(resource("a")))
        val model = InventoryViewModel(source)
        runImmediate { model.refresh() }
        source.failScan = true
        runImmediate { model.scan() }

        assertEquals(listOf("a"), model.state.resources!!.map { it.name })
        assertEquals("Azure scan failed", model.state.error)
        assertFalse(model.state.scanning)
    }

    @Test
    fun anUnexpectedErrorNeverShowsItsDetail() {
        val model = InventoryViewModel(object : InventorySource {
            override suspend fun resources(): List<InventoryResource> = error("jdbc:postgresql://internal")
            override suspend fun scan(): ScanSummary = error("unused")
        })
        runImmediate { model.refresh() }

        assertFalse(model.state.error!!.contains("jdbc"))
        assertNull(model.state.resources)
    }

    @Test
    fun costIsShownWithTwoDecimals() {
        assertEquals("0.02", formatCost(0.0249))
        assertEquals("12.50", formatCost(12.5))
        assertEquals("3.00", formatCost(2.999))
    }
}
