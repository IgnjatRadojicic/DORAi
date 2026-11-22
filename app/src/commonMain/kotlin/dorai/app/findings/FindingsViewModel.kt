package dorai.app.findings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dorai.app.components.EvidenceCitation
import dorai.app.components.FindingSeverity
import dorai.app.components.Provenance
import dorai.app.components.displayMessage
import dorai.app.components.retryable
import kotlinx.coroutines.CancellationException

data class PlanDocument(val name: String, val bytes: ByteArray) {
    companion object { const val MAX_BYTES = 10_000_000 }
}

data class ChangeFinding(
    val ruleId: String,
    val severity: FindingSeverity,
    val title: String,
    val resourceAddress: String,
    val detail: String,
    val templates: List<String> = emptyList(),
    val location: String? = null,
    val functionId: String? = null,
    val functionName: String? = null,
    val criticalFunction: Boolean = false,
    val citation: EvidenceCitation? = null,
    val explanation: String? = null,
    val provenance: Provenance = Provenance.PLANNED,
)

/** Implement in the application composition root by delegating to the shared API client. */
fun interface FindingsSource {
    suspend fun checkPlan(plan: PlanDocument): List<ChangeFinding>
}

data class FindingsState(
    val plan: PlanDocument? = null,
    val busy: Boolean = false,
    val checked: Boolean = false,
    val findings: List<ChangeFinding> = emptyList(),
    val filter: FindingSeverity? = null,
    val error: String? = null,
    /** False when the server refused the plan itself; checking the same file again can't help. */
    val canRetry: Boolean = true,
) {
    val visible: List<ChangeFinding> get() = findings.filter { filter == null || it.severity == filter }
        .sortedWith(compareBy<ChangeFinding> { it.severity.ordinal }.thenBy { it.resourceAddress }.thenBy { it.ruleId })
}

class FindingsViewModel(private val source: FindingsSource) : ViewModel() {
    var state by mutableStateOf(FindingsState())
        private set

    /** The last plan that was checked with its results; a new plan that fails brings them back. */
    private var lastChecked: FindingsState? = null

    fun selectPlan(plan: PlanDocument?) {
        if (state.busy || plan == null) return
        val error = when {
            !plan.name.endsWith(".json", ignoreCase = true) -> "Choose a JSON plan file."
            plan.bytes.isEmpty() -> "The selected file is empty."
            plan.bytes.size > PlanDocument.MAX_BYTES -> "The plan must be no larger than 10 MB."
            else -> null
        }
        // A file that can't be a plan never replaces the plan already loaded.
        state = if (error != null) state.copy(error = error, canRetry = false) else FindingsState(plan = plan)
    }

    fun showFileError(error: Throwable) {
        if (!state.busy) state = state.copy(error = error.displayMessage("The plan could not be opened."), canRetry = false)
    }

    fun removePlan() {
        if (state.busy) return
        lastChecked = null
        state = FindingsState()
    }

    fun filter(severity: FindingSeverity?) { state = state.copy(filter = severity) }

    suspend fun check() {
        val plan = state.plan ?: return
        if (state.busy) return
        state = state.copy(busy = true, checked = false, findings = emptyList(), error = null)
        try {
            state = state.copy(findings = source.checkPlan(plan).toList(), checked = true)
            lastChecked = state.copy(busy = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val message = error.displayMessage("The plan check failed. Check the connection and try again.")
            // When the server refuses a new file, the earlier plan and its results come back with the message.
            val earlier = lastChecked?.takeIf { !error.retryable && it.plan != plan }
            state = earlier?.copy(error = "${plan.name}: $message ${earlier.plan?.name} is still shown.", canRetry = false)
                ?: state.copy(error = message, canRetry = error.retryable)
        } finally {
            state = state.copy(busy = false)
        }
    }
}
