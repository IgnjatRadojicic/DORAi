package dorai.server

import dorai.server.register.lei.GleifDirectory
import dorai.server.register.referenceDate
import dorai.shared.roi.RoiView
import dorai.server.ai.AiConfig
import dorai.server.ai.withAi
import dorai.server.check.checkRoutes
import dorai.server.auth.AuthConfig
import dorai.server.auth.AuthService
import dorai.server.auth.accessControl
import dorai.server.auth.authRoutes
import dorai.server.auth.installAuthRateLimit
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
import dorai.shared.Version
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
import dorai.server.scan.ScanSchedule
import dorai.server.scan.runScan
import kotlinx.coroutines.launch

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
        module = {
            module(services)
            ScanSchedule.fromEnv()?.let { every ->
                log.info("Scheduled scan every {}.", every)
                // Application is a coroutine scope: the loop stops with the server.
                launch {
                    ScanSchedule.loop(every, { runScan(services) }) { result ->
                        result.onSuccess { log.info("Scheduled scan: {} resources ({}).", it.resourceCount, it.mode) }
                            .onFailure { log.warn("Scheduled scan failed: {}", it.message) }
                    }
                }
            }
        },
    ).start(wait = true)
}

/**
 * Storage: PostgreSQL with the AI services (regulation search, classifier, suggester, explainer, drafter) when DORAI_DB_URL is set, otherwise fixtures in memory.
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
        postgresServices(db, data).copy(auth = auth).withAi(db, AiConfig.fromEnv())
    }
    val gleif = System.getenv("DORAI_GLEIF").equals("off", ignoreCase = true)
    val withLei = if (gleif) base else base.copy(leiDirectory = GleifDirectory(System.getenv("DORAI_GLEIF_URL") ?: GleifDirectory.DEFAULT_URL))
    val live = LiveConfig.fromEnv()
    val configured = if (live == null) withLei else {
        log.info("Live scan of {}{}.", live.clouds, live.awsEndpoint?.let { " (AWS through $it)" } ?: "")
        withLei.withLiveScan(live, data.costs)
    }
    // The PDF's AI summary uses the drafting service this server is configured with.
    return configured.copy(exporter = dorai.server.export.RoiExporter(configured.reportDrafter, configured.validator))
}

/** Fixture wiring: in-memory storage, no AI. Used by tests and by a server started without a database. */
fun Application.module() = module(fixtureServices())

fun Application.module(services: Services) {
    install(ContentNegotiation) { json(DoraiJson) }
    installProblems()
    // Before access control: an outdated client hears "update" rather than "log in".
    install(VersionCheck)

    services.auth?.let {
        install(accessControl(it))
        installAuthRateLimit(it)
    }

    routing {
        route(Dorai.API_PREFIX) {
            get("health") { call.respond(Health(status = "ok", app = Dorai.NAME, version = Version.CURRENT.toString())) }
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
        val register = services.register.register()
        val resources = services.register.resources()
        val date = call.referenceDate()
        val draft = call.request.queryParameters["draft"] == "true"
        // The final package only when nothing is missing; a draft shows progress and is named as one.
        if (!draft) {
            val view = RoiView.of(register, resources, date)
            if (!view.ready) {
                throw ApiProblem(
                    HttpStatusCode.UnprocessableEntity,
                    "Register not ready for export",
                    "Resolve register validation errors before the final package: ${view.errorCount} remain (see GET /api/roi). Ask for a draft with ?draft=true.",
                )
            }
        }
        val written = exporter.reportPackage(register, resources, date, draft)
        call.response.header(HttpHeaders.ContentDisposition, attachment(written.fileName))
        call.respondBytes(written.bytes, ContentType.Application.Zip)
    }
    get("pdf") {
        val exporter = services.exporter ?: throw exportUnavailable()
        val register = services.register.register()
        // The firm's latest checked plan, never the demo one: without a plan the report says so.
        val plan = services.plans.latest()
        val findings = plan?.let { services.planChecker.check(it.json, register) }.orEmpty()
        val source = plan?.let { "Findings from the Terraform plan ${it.checkedBy} checked at ${it.checkedAt}." }
            ?: "No Terraform plan has been checked on this server yet, so there are no plan findings. Check a plan under Findings first."
        // Built before the attachment header, so a failure is answered as problem details.
        val bytes = exporter.pdf(register, services.register.resources(), call.referenceDate(), findings, source)
        call.response.header(HttpHeaders.ContentDisposition, attachment("dorai-report.pdf"))
        call.respondBytes(bytes, ContentType.Application.Pdf)
    }
}

private fun exportUnavailable() =
    ApiProblem(HttpStatusCode.NotImplemented, "Export not available yet", "No exporter is configured on this server.")

private fun attachment(fileName: String) =
    ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, fileName).toString()
