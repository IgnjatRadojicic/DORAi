package dorai.server

import dorai.shared.model.DoraiJson
import dorai.shared.model.Problem
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respondText

/** Throw from a route to answer with problem details (RFC 9457). */
class ApiProblem(
    val status: HttpStatusCode,
    val title: String,
    val detail: String? = null,
) : RuntimeException(detail ?: title)

private val ProblemJson = ContentType("application", "problem+json")

fun Application.installProblems() {
    install(StatusPages) {
        exception<ApiProblem> { call, e -> call.respondProblem(e.status, e.title, e.detail) }
        exception<BadRequestException> { call, e ->
            call.respondProblem(HttpStatusCode.BadRequest, "Bad request", e.message)
        }
        exception<Throwable> { call, e ->
            call.application.log.error("Unhandled error on ${call.request.path()}", e)
            call.respondProblem(HttpStatusCode.InternalServerError, "Internal server error")
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respondProblem(status, "Not found", "No endpoint at ${call.request.path()}.")
        }
    }
}

suspend fun ApplicationCall.respondProblem(status: HttpStatusCode, title: String, detail: String? = null) {
    val problem = Problem(title = title, status = status.value, detail = detail, instance = request.path())
    respondText(DoraiJson.encodeToString(problem), ProblemJson, status)
}
