package dorai.server

import dorai.server.ai.AiConfig
import dorai.server.ai.regulationSearch
import dorai.server.check.checkRoutes
import dorai.server.core.Services
import dorai.server.db.DatabaseConfig
import dorai.server.db.connectDatabase
import dorai.server.fixtures.fixtureServices
import dorai.server.register.registerRoutes
import dorai.server.review.suggestionRoutes
import dorai.server.scan.scanRoutes
import dorai.shared.Dorai
import dorai.shared.model.DoraiJson
import dorai.shared.model.Health
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun main() {
    val port = System.getenv("DORAI_PORT")?.toIntOrNull() ?: 8080
    val services = serverServices()
    embeddedServer(Netty, port = port) { module(services) }.start(wait = true)
}

/** Phase 1 wiring: fixtures, in-memory storage, no AI. Later phases pass real services. */
fun Application.module() = module(fixtureServices())

/** Fixtures, plus regulation search on PostgreSQL with pgvector when `DORAI_DB_URL` is set. */
private fun serverServices(): Services {
    val base = fixtureServices()
    val database = DatabaseConfig.fromEnv()?.let(::connectDatabase) ?: return base
    return base.copy(regulationSearch = regulationSearch(database, AiConfig.fromEnv()))
}

fun Application.module(services: Services) {
    install(ContentNegotiation) { json(DoraiJson) }
    installProblems()

    routing {
        route(Dorai.API_PREFIX) {
            get("health") { call.respond(Health(status = "ok", app = Dorai.NAME)) }
            scanRoutes(services)
            registerRoutes(services)
            suggestionRoutes(services)
            checkRoutes(services)
            exportRoutes(services)
        }
    }
}

private fun Route.exportRoutes(services: Services) = route("export") {
    get("csv") {
        val exporter = services.exporter ?: throw exportUnavailable()
        call.response.header(HttpHeaders.ContentDisposition, attachment("dorai-register.zip"))
        call.respondBytes(exporter.csvZip(services.register.register()), ContentType.Application.Zip)
    }
    get("pdf") {
        val exporter = services.exporter ?: throw exportUnavailable()
        val register = services.register.register()
        val findings = services.planChecker.check(services.demoPlan, register)
        call.response.header(HttpHeaders.ContentDisposition, attachment("dorai-report.pdf"))
        call.respondBytes(exporter.pdf(register, findings), ContentType.Application.Pdf)
    }
}

private fun exportUnavailable() =
    ApiProblem(HttpStatusCode.NotImplemented, "Export not available yet", "No exporter is configured on this server.")

private fun attachment(fileName: String) =
    ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, fileName).toString()
