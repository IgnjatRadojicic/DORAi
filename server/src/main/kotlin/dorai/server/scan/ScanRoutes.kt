package dorai.server.scan

import dorai.server.core.Services
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.scanRoutes(services: Services) {
    post("scan") { call.respond(runScan(services)) }
    get("resources") { call.respond(services.register.resources()) }
    get("concentration") {
        val register = services.register
        call.respond(concentration(register.resources(), register.costs(), register.register().functions))
    }
}
