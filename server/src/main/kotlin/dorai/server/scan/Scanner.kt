package dorai.server.scan

import dorai.server.ApiProblem
import dorai.server.collect.withMonthlyCost
import dorai.server.core.Services
import dorai.shared.model.ScanMode
import dorai.shared.model.ScanResult
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** One server per install: a scheduled scan and a button press never run at the same time. */
private val scanLock = Mutex()

/**
 * Scans every configured cloud and the last 12 months of costs, then replaces the stored scan.
 * One failing cloud fails the scan, so a partial inventory never replaces the stored one.
 */
suspend fun runScan(services: Services, clock: Clock = Clock.System): ScanResult = scanLock.withLock {
    val collected = services.collectors.map { collector ->
        try {
            collector.collect()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiProblem(HttpStatusCode.BadGateway, "${collector.provider} scan failed", e.message ?: e::class.simpleName)
        }
    }
    val today = clock.todayIn(TimeZone.UTC)
    val from = LocalDate(today.year, today.month, 1).minus(12, DateTimeUnit.MONTH)
    val costs = services.costs.costs(from, today)
    val resources = collected.flatMap { it.resources }.withMonthlyCost(costs.entries)
    services.register.saveScan(resources, costs.entries)
    ScanResult(
        mode = if (collected.any { it.mode == ScanMode.Fixture }) ScanMode.Fixture else ScanMode.Live,
        scannedAt = clock.now(),
        resourceCount = resources.size,
        newSuggestionCount = services.suggestions.pending().size,
        costSource = costs.source,
    )
}
