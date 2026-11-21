package dorai.server.review

import dorai.server.ApiProblem
import dorai.shared.model.SuggestionStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.suggestionRoutes(store: SuggestionStore) {
    route("suggestions") {
        get { call.respond(store.all()) }

        // Phase 1: returns the pending fixture suggestions; the AI generates real ones later.
        post("generate") {
            val resourceId = call.request.queryParameters["resourceId"]?.takeIf { it.isNotBlank() }
            call.respond(store.pending(resourceId))
        }

        post("{id}/accept") { decide(store, SuggestionStatus.Accepted) }
        post("{id}/reject") { decide(store, SuggestionStatus.Rejected) }
    }
}

private suspend fun RoutingContext.decide(store: SuggestionStore, status: SuggestionStatus) {
    val id = call.parameters["id"].orEmpty()
    when (val result = store.decide(id, status)) {
        is SuggestionStore.Decision.Done -> call.respond(result.suggestion)
        is SuggestionStore.Decision.AlreadyDecided -> throw ApiProblem(
            HttpStatusCode.Conflict,
            "Suggestion already decided",
            "Suggestion $id is already ${result.suggestion.status}.",
        )
        SuggestionStore.Decision.NotFound -> throw ApiProblem(
            HttpStatusCode.NotFound,
            "Suggestion not found",
            "No suggestion with id $id.",
        )
    }
}
