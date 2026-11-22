package dorai.server.scan

import dorai.server.fixtures.fixtureServices
import dorai.shared.model.ScanResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class ScanScheduleTest {
    @Test
    fun theIntervalComesFromTheEnvironment() {
        assertNull(ScanSchedule.fromEnv { null })
        assertNull(ScanSchedule.fromEnv { "off" })
        assertEquals(24.hours, ScanSchedule.fromEnv { "24h" })
        assertEquals(12.hours, ScanSchedule.fromEnv { "PT12H" })
        assertFailsWith<IllegalArgumentException> { ScanSchedule.fromEnv { "daily" } }
        assertFailsWith<IllegalArgumentException> { ScanSchedule.fromEnv { "10m" } }
    }

    @Test
    fun scansOnEachIntervalAndKeepsGoingAfterAFailure() = runTest {
        val services = fixtureServices()
        var calls = 0
        val results = mutableListOf<Result<ScanResult>>()
        val job = launch {
            ScanSchedule.loop(6.hours, {
                calls++
                if (calls == 1) error("cloud down") else runScan(services)
            }) { results += it }
        }
        advanceTimeBy(6.hours - 1.minutes)
        runCurrent()
        assertEquals(0, calls, "nothing runs before the first interval")
        advanceTimeBy(6.hours + 2.minutes)
        runCurrent()
        job.cancel()
        assertEquals(2, calls)
        assertTrue(results[0].isFailure)
        assertTrue(results[1].getOrThrow().resourceCount > 0)
    }
}
