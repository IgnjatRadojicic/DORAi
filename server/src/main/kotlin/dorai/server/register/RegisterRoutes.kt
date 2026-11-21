package dorai.server.register

import dorai.server.fixtures.FixtureData
import dorai.shared.model.ValidationReport
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.registerRoutes(fixtures: FixtureData) {
    get("register") { call.respond(fixtures.register) }
    // Phase 1: the validator lands in phase 2; until then the register reports as valid.
    get("validation") { call.respond(ValidationReport(isValid = true, errorCount = 0, warningCount = 0, issues = emptyList())) }
}
