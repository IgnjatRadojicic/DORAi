package dorai.server

import dorai.shared.Dorai
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

fun main() {
    val port = System.getenv("DORAI_PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, module = Application::module).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) { json() }
    routing {
        get("${Dorai.API_PREFIX}/health") { call.respond(Health(status = "ok", app = Dorai.NAME)) }
    }
}

@Serializable
data class Health(val status: String, val app: String)
