package dorai.server.review

import dorai.server.ApiProblem
import dorai.server.core.AiAnswer
import dorai.server.core.Services
import dorai.server.register.RegisterEditor
import dorai.server.register.who
import dorai.server.core.SuggestionRepository.Decision
import dorai.shared.model.Resource
import dorai.shared.model.Suggestion
import dorai.shared.model.SuggestionKind
import dorai.shared.model.SuggestionStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID
import kotlin.time.Clock

fun Route.suggestionRoutes(services: Services) {
    route("suggestions") {
        get { call.respond(services.suggestions.all()) }

        post("generate") {
            val resourceId = call.request.queryParameters["resourceId"]?.takeIf { it.isNotBlank() }
            generate(services, resourceId)
            call.respond(services.suggestions.pending(resourceId))
        }

        post("{id}/accept") { decide(services, SuggestionStatus.Accepted) }
        post("{id}/reject") { decide(services, SuggestionStatus.Rejected) }
    }
}

/**
 * Asks the AI about resources that have no suggestion yet: a service type when it is unknown,
 * a business function when the resource has none. Only cited answers become suggestions.
 */
private suspend fun generate(services: Services, resourceId: String?) {
    val all = services.register.resources()
    val targets = if (resourceId == null) all else all.filter { it.id == resourceId }
    if (resourceId != null && targets.isEmpty()) {
        throw ApiProblem(HttpStatusCode.NotFound, "Resource not found", "No resource with id $resourceId.")
    }
    val covered = services.suggestions.all().map { it.resourceId to it.kind }.toSet()
    val functions = services.register.register().functions
    val now = Clock.System.now()

    val new = targets.flatMap { resource ->
        buildList {
            if (resource.serviceType == null && (resource.id to SuggestionKind.ServiceType) !in covered) {
                services.classifier.classify(resource).toSuggestion(resource, SuggestionKind.ServiceType, now) { it.name }?.let(::add)
            }
            if (resource.functionId == null && (resource.id to SuggestionKind.BusinessFunction) !in covered) {
                services.functionSuggester.suggest(resource, functions).toSuggestion(resource, SuggestionKind.BusinessFunction, now) { it }?.let(::add)
            }
        }
    }
    services.suggestions.add(new)
}

private fun <T> AiAnswer<T>.toSuggestion(
    resource: Resource,
    kind: SuggestionKind,
    now: kotlin.time.Instant,
    value: (T) -> String,
): Suggestion? = (this as? AiAnswer.Answer<T>)?.let {
    Suggestion(
        id = UUID.randomUUID().toString(),
        resourceId = resource.id,
        resourceName = resource.name,
        kind = kind,
        value = value(it.value),
        reason = it.reason,
        citations = it.citations,
        confidence = it.confidence,
        status = SuggestionStatus.Pending,
        createdAt = now,
    )
}

private suspend fun RoutingContext.decide(services: Services, status: SuggestionStatus) {
    val id = call.parameters["id"].orEmpty()
    when (val result = services.suggestions.decide(id, status)) {
        is Decision.Done -> {
            val s = result.suggestion
            // An accepted register value goes into the register at once, as the person's value.
            if (status == SuggestionStatus.Accepted && s.kind == SuggestionKind.RoiValue && s.column != null) {
                RegisterEditor(services.register).acceptValue(s.resourceId, s.column!!, s.value, s.citations.firstOrNull(), s.reason, call.who())
            }
            call.respond(s)
        }
        is Decision.AlreadyDecided -> throw ApiProblem(
            HttpStatusCode.Conflict,
            "Suggestion already decided",
            "Suggestion $id is already ${result.suggestion.status}.",
        )
        Decision.NotFound -> throw ApiProblem(HttpStatusCode.NotFound, "Suggestion not found", "No suggestion with id $id.")
    }
}
