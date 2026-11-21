package dorai.server

import dorai.server.check.checkRoutes
import dorai.server.auth.AuthConfig
import dorai.server.auth.AuthService
import dorai.server.auth.accessControl
import dorai.server.auth.authRoutes
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
import dorai.server.tls.Tls
import dorai.server.tls.TlsConfig
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
import io.ktor.server.engine.connector
import io.ktor.server.engine.sslConnector
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
    val log = LoggerFactory.getLogger("dorai.server")
    val port = System.getenv("DORAI_PORT")?.toIntOrNull() ?: 8080
    val tls = TlsConfig.fromEnv()
    val keyStore = tls?.let(Tls::loadOrCreate)
    val fingerprint = keyStore?.let(Tls::fingerprint)
    if (tls != null) log.info("HTTPS on port {}. Certificate SHA-256: {}", tls.port, fingerprint)

    val defaultPublicUrl = if (tls != null) "https://localhost:${tls.port}" else "http://localhost:$port"
    val services = servicesFromEnv(fingerprint, defaultPublicUrl)

    embeddedServer(
        Netty,
        configure = {
            // Plain HTTP stays for the health check and local admin; compose publishes it on the host only.
            connector { this.port = port }
            if (tls != null && keyStore != null) {
                sslConnector(keyStore, Tls.ALIAS, { tls.password.toCharArray() }, { tls.password.toCharArray() }) { this.port = tls.port }
            }
        },
        module = { module(services) },
    ).start(wait = true)
}

/**
 * Storage: PostgreSQL when DORAI_DB_URL is set, otherwise fixtures in memory.
 * Auth: on with a database (DORAI_JWT_SECRET required), off on fixtures in memory.
 * Scan: the firm's clouds when DORAI_DATA_SOURCE=live, otherwise the FinPay fixtures.
 */
fun servicesFromEnv(fingerprint: String? = null, defaultPublicUrl: String = "http://localhost:8080"): Services {
    val log = LoggerFactory.getLogger("dorai.server")
    val data = FixtureData.load()
    val dbConfig = DbConfig.fromEnv()
    val base = if (dbConfig == null) {
        log.info("DORAI_DB_URL is not set: running on fixtures in memory, without auth.")
        fixtureServices(data)
    } else {
        val db = Db.open(dbConfig)
        val auth = AuthService(db, AuthConfig.fromEnv(defaultPublicUrl = defaultPublicUrl), fingerprint)
        auth.bootstrap()
        postgresServices(db, data).copy(auth = auth)
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

    services.auth?.let { install(accessControl(it)) }

    routing {
        route(Dorai.API_PREFIX) {
            get("health") { call.respond(Health(status = "ok", app = Dorai.NAME)) }
            services.auth?.let { authRoutes(it) }
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
