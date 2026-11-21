package dorai.server.register

import dorai.server.ApiProblem
import dorai.server.auth.principal
import dorai.server.core.Services
import dorai.shared.model.BusinessFunction
import dorai.shared.model.Contract
import dorai.shared.model.FinancialEntity
import dorai.shared.model.Provider
import dorai.shared.model.RoiRow
import dorai.shared.roi.RoiView
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

fun Route.registerRoutes(services: Services) {
    val editor = RegisterEditor(services.register)

    get("register") { call.respond(services.register.register()) }
    get("validation") { call.respond(services.validator.validate(services.register.register())) }
    get("roi") { call.respond(RoiView.of(services.register.register(), services.register.resources(), call.referenceDate())) }

    route("register") {
        put("entity") { call.respond(editor.putEntity(call.receive<FinancialEntity>(), call.who())) }

        route("providers/{id}") {
            put { call.respond(editor.putProvider(call.id(), call.receive<Provider>(), call.who())) }
            delete { editor.deleteProvider(call.id()); call.respond(HttpStatusCode.NoContent) }
        }
        route("contracts/{id}") {
            put { call.respond(editor.putContract(call.id(), call.receive<Contract>(), call.who())) }
            delete { editor.deleteContract(call.id()); call.respond(HttpStatusCode.NoContent) }
        }
        route("functions/{id}") {
            put { call.respond(editor.putFunction(call.id(), call.receive<BusinessFunction>(), call.who())) }
            delete { editor.deleteFunction(call.id()); call.respond(HttpStatusCode.NoContent) }
        }
        route("rows/{template}/{id}") {
            put { call.respond(editor.putRow(call.parameters["template"].orEmpty(), call.id(), call.receive<RoiRow>(), call.who())) }
            delete { editor.deleteRow(call.parameters["template"].orEmpty(), call.id()); call.respond(HttpStatusCode.NoContent) }
        }
    }
}

private fun RoutingCall.id() = parameters["id"].orEmpty()

/** The person making the change, for provenance; "development" on a server without auth. */
internal fun RoutingCall.who() = principal?.username ?: "development"

/** The RoI reference date from `?date=yyyy-mm-dd`, or today. */
internal fun RoutingCall.referenceDate(): LocalDate {
    val raw = request.queryParameters["date"]?.takeIf { it.isNotBlank() } ?: return Clock.System.todayIn(TimeZone.UTC)
    return runCatching { LocalDate.parse(raw) }.getOrElse {
        throw ApiProblem(HttpStatusCode.BadRequest, "Invalid date", "Reference date must be yyyy-mm-dd, got '$raw'.")
    }
}
