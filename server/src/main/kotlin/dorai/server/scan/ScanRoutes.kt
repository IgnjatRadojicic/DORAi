package dorai.server.scan

import dorai.server.fixtures.FixtureData
import dorai.server.review.SuggestionStore
import dorai.shared.model.CostSource
import dorai.shared.model.ScanMode
import dorai.shared.model.ScanResult
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlin.time.Clock

fun Route.scanRoutes(fixtures: FixtureData, suggestions: SuggestionStore) {
    post("scan") {
        call.respond(
            ScanResult(
                mode = ScanMode.Fixture,
                scannedAt = Clock.System.now(),
                resourceCount = fixtures.resources.size,
                newSuggestionCount = suggestions.pending().size,
                costSource = CostSource.Fixture,
            ),
        )
    }
    get("resources") { call.respond(fixtures.resources) }
    get("concentration") {
        call.respond(concentration(fixtures.resources, fixtures.scan.costs, fixtures.register.functions))
    }
}
