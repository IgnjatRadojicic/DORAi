package dorai.server

import dorai.server.ai.AiConfig
import dorai.server.ai.regulationSearch
import dorai.server.check.checkRoutes
import dorai.server.collect.LiveConfig
import dorai.server.collect.withLiveScan
import dorai.server.core.Services
import dorai.server.db.Db
import dorai.server.db.DbConfig
import dorai.server.db.postgresServices
import dorai.server.fixtures.FixtureData
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
import org.slf4j.LoggerFactory

fun main() {
    val port = System.getenv("DORAI_PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port) { module(servicesFromEnv()) }.start(wait = true)
}

/**
 * Storage: PostgreSQL with pgvector regulation search when DORAI_DB_URL is set, otherwise fixtures in memory.
 * Scan: the firm's clouds when DORAI_DATA_SOURCE=live, otherwise the FinPay fixtures.
 */
fun servicesFromEnv(): Services {
    val log = LoggerFactory.getLogger("dorai.server")
    val data = FixtureData.load()
    val db = DbConfig.fromEnv()
    val base = if (db == null) {
        log.info("DORAI_DB_URL is not set: running on fixtures in memory.")
        fixtureServices(data)
    } else {
        val database = Db.open(db)
        postgresServices(database, data).copy(regulationSearch = regulationSearch(database, AiConfig.fromEnv()))
    }
    val live = LiveConfig.fromEnv() ?: return base
    log.info("Live scan of {}{}.", live.clouds, live.awsEndpoint?.let { " (AWS through $it)" } ?: "")
    return base.withLiveScan(live, data.costs)
}

/** Fixture wiring: in-memory storage, no AI. Used by tests and by a server started without a database. */
fun Application.module() = module(fixtureServices())

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
