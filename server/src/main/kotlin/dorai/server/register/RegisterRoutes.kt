package dorai.server.register

import dorai.server.core.Services
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.registerRoutes(services: Services) {
    get("register") { call.respond(services.register.register()) }
    get("validation") { call.respond(services.validator.validate(services.register.register())) }
}
