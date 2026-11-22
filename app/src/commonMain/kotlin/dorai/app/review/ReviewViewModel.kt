package dorai.app.review

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dorai.app.components.EvidenceCitation
import dorai.app.components.displayMessage
import kotlinx.coroutines.CancellationException

enum class SuggestionField(val label: String) { SERVICE_TYPE("Service type"), BUSINESS_FUNCTION("Business function"), ROI_VALUE("Register value") }

enum class ReviewStatus { PENDING, ACCEPTED, REJECTED }

/** One AI suggestion. It always carries at least one citation: the server keeps no uncited answer. */
data class ReviewSuggestion(
    val id: String,
    val resourceName: String,
    val field: SuggestionField,
    val value: String,
    val valueLabel: String?,
    val reason: String,
    val citations: List<EvidenceCitation>,
    val confidence: Double?,
    val status: ReviewStatus,
    /** For a register value: the RoI column it fills, as "B_02.02.0170 Sensitiveness of the data ...". */
    val fieldLabel: String? = null,
)

interface ReviewSource {
    suspend fun suggestions(): List<ReviewSuggestion>
    /** Asks the AI about resources that have no suggestion yet. */
    suspend fun generate()
    suspend fun decide(id: String, accept: Boolean): ReviewSuggestion
}

data class ReviewState(
    val loading: Boolean = false,
    val generating: Boolean = false,
    val deciding: String? = null,
    val suggestions: List<ReviewSuggestion>? = null,
    val error: String? = null,
    /** The result of the last Get AI suggestions, shown once as a message; [noticeCount] tells repeats apart. */
    val notice: String? = null,
    val noticeCount: Int = 0,
) {
    val busy: Boolean get() = loading || generating || deciding != null
    val pending: List<ReviewSuggestion> get() = suggestions.orEmpty().filter { it.status == ReviewStatus.PENDING }
    val decided: List<ReviewSuggestion> get() = suggestions.orEmpty().filter { it.status != ReviewStatus.PENDING }
}

/** The AI only suggests; a person accepts or rejects each suggestion here. */
class ReviewViewModel(private val source: ReviewSource) : ViewModel() {
    var state by mutableStateOf(ReviewState())
        private set

    suspend fun refresh() {
        if (state.busy) return
        state = state.copy(loading = true, error = null)
        try {
            state = state.copy(loading = false, suggestions = source.suggestions())
        } catch (cancelled: CancellationException) {
            state = state.copy(loading = false)
            throw cancelled
        } catch (error: Exception) {
            state = state.copy(loading = false, error = error.displayMessage("The suggestions could not be loaded. Check the connection and retry."))
        }
    }

    suspend fun generate() {
        if (state.busy) return
        state = state.copy(generating = true, error = null)
        try {
            val before = state.pending.size
            source.generate()
            val suggestions = source.suggestions()
            val added = suggestions.count { it.status == ReviewStatus.PENDING } - before
            state = state.copy(generating = false, suggestions = suggestions,
                notice = if (added > 0) "The AI added $added suggestions to review." else "The AI found nothing new to suggest.",
                noticeCount = state.noticeCount + 1)
        } catch (cancelled: CancellationException) {
            state = state.copy(generating = false)
            throw cancelled
        } catch (error: Exception) {
            state = state.copy(generating = false, error = error.displayMessage("The AI could not make suggestions. Retry in a minute."))
        }
    }

    suspend fun accept(id: String) = decide(id, accept = true)

    suspend fun reject(id: String) = decide(id, accept = false)

    private suspend fun decide(id: String, accept: Boolean) {
        if (state.busy || state.suggestions.orEmpty().none { it.id == id && it.status == ReviewStatus.PENDING }) return
        state = state.copy(deciding = id, error = null)
        try {
            val decided = source.decide(id, accept)
            state = state.copy(deciding = null, suggestions = state.suggestions.orEmpty().map { if (it.id == id) decided else it })
        } catch (cancelled: CancellationException) {
            state = state.copy(deciding = null)
            throw cancelled
        } catch (error: Exception) {
            // Someone else may have decided it meanwhile, so show the server's current list with the message.
            val message = error.displayMessage("The decision was not saved. Retry.")
            state = state.copy(deciding = null)
            refresh()
            state = state.copy(error = message)
        }
    }
}
