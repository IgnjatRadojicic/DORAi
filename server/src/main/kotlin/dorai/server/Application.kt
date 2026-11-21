package dorai.server

import dorai.server.check.checkRoutes
import dorai.server.fixtures.FixtureData
import dorai.server.register.registerRoutes
import dorai.server.review.SuggestionStore
import dorai.server.review.suggestionRoutes
import dorai.server.scan.scanRoutes
import dorai.shared.Dorai
import dorai.shared.model.DoraiJson
import dorai.shared.model.Health
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun main() {
    val port = System.getenv("DORAI_PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, module = Application::module).start(wait = true)
}

/** Phase 1: every endpoint serves fixture data, so the apps can be built before the database lands. */
fun Application.module() = module(FixtureData.load())

fun Application.module(fixtures: FixtureData) {
    install(ContentNegotiation) { json(DoraiJson) }
    installProblems()

    val suggestions = SuggestionStore(fixtures.suggestions)

    routing {
        route(Dorai.API_PREFIX) {
            get("health") { call.respond(Health(status = "ok", app = Dorai.NAME)) }
            scanRoutes(fixtures, suggestions)
            registerRoutes(fixtures)
            suggestionRoutes(suggestions)
            checkRoutes(fixtures)
            route("export") {
                get("csv") { throw ApiProblem(HttpStatusCode.NotImplemented, "Export not available yet", "CSV export arrives in phase 5.") }
                get("pdf") { throw ApiProblem(HttpStatusCode.NotImplemented, "Export not available yet", "PDF export arrives in phase 5.") }
            }
        }
    }
}
