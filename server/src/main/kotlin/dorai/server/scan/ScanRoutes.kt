package dorai.server.scan

import dorai.server.core.Services
import dorai.shared.model.ScanMode
import dorai.shared.model.ScanResult
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import dorai.server.ApiProblem
import dorai.server.collect.withMonthlyCost
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

fun Route.scanRoutes(services: Services) {
    post("scan") {
        // One failing cloud fails the scan, so a partial inventory never replaces the stored one.
        val collected = services.collectors.map { collector ->
            try {
                collector.collect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw ApiProblem(HttpStatusCode.BadGateway, "${collector.provider} scan failed", e.message ?: e::class.simpleName)
            }
        }
        val today = Clock.System.todayIn(TimeZone.UTC)
        val from = LocalDate(today.year, today.month, 1).minus(12, DateTimeUnit.MONTH)
        val costs = services.costs.costs(from, today)
        val resources = collected.flatMap { it.resources }.withMonthlyCost(costs.entries)
        services.register.saveScan(resources, costs.entries)

        call.respond(
            ScanResult(
                mode = if (collected.any { it.mode == ScanMode.Fixture }) ScanMode.Fixture else ScanMode.Live,
                scannedAt = Clock.System.now(),
                resourceCount = resources.size,
                newSuggestionCount = services.suggestions.pending().size,
                costSource = costs.source,
            ),
        )
    }
    get("resources") { call.respond(services.register.resources()) }
    get("concentration") {
        val register = services.register
        call.respond(concentration(register.resources(), register.costs(), register.register().functions))
    }
}
