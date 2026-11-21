package dorai.server.review

import dorai.shared.model.Suggestion
import dorai.shared.model.SuggestionStatus
import kotlin.time.Clock

/** In-memory suggestions for phase 1; the database replaces it in phase 2. */
class SuggestionStore(initial: List<Suggestion>, private val clock: Clock = Clock.System) {

    private val items = LinkedHashMap<String, Suggestion>().apply { initial.forEach { put(it.id, it) } }

    @Synchronized
    fun all(): List<Suggestion> = items.values.toList()

    @Synchronized
    fun pending(resourceId: String? = null): List<Suggestion> =
        items.values.filter { it.status == SuggestionStatus.Pending && (resourceId == null || it.resourceId == resourceId) }

    @Synchronized
    fun find(id: String): Suggestion? = items[id]

    /** Accepts or rejects a pending suggestion. Returns null if it does not exist, or [Decision.AlreadyDecided]. */
    @Synchronized
    fun decide(id: String, status: SuggestionStatus): Decision {
        val current = items[id] ?: return Decision.NotFound
        if (current.status != SuggestionStatus.Pending) return Decision.AlreadyDecided(current)
        val decided = current.copy(status = status, decidedAt = clock.now())
        items[id] = decided
        return Decision.Done(decided)
    }

    sealed interface Decision {
        data class Done(val suggestion: Suggestion) : Decision
        data class AlreadyDecided(val suggestion: Suggestion) : Decision
        data object NotFound : Decision
    }
}
