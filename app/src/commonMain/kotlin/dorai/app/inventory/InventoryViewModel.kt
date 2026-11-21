package dorai.app.inventory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dorai.app.components.Provenance
import dorai.app.components.displayMessage
import kotlinx.coroutines.CancellationException

/** A register value with where it came from: scanned, ai-suggested (pending) or human-confirmed. */
data class SourcedValue(val value: String, val provenance: Provenance, val label: String? = null)

data class InventoryResource(
    val id: String,
    val name: String,
    val type: String,
    val provider: String,
    val location: String,
    val country: String?,
    val serviceType: SourcedValue?,
    val function: SourcedValue?,
    val monthlyCost: Double?,
    val currency: String?,
)

data class ScanSummary(val live: Boolean, val scannedAt: String, val resourceCount: Int, val newSuggestionCount: Int, val liveCost: Boolean)

interface InventorySource {
    suspend fun resources(): List<InventoryResource>
    suspend fun scan(): ScanSummary
}

data class InventoryState(
    val loading: Boolean = false,
    val scanning: Boolean = false,
    val resources: List<InventoryResource>? = null,
    val lastScan: ScanSummary? = null,
    val error: String? = null,
) {
    val busy: Boolean get() = loading || scanning
}

class InventoryViewModel(private val source: InventorySource) : ViewModel() {
    var state by mutableStateOf(InventoryState())
        private set

    /** Loads the stored inventory. A failed load keeps the last list: the server never replaces it partly. */
    suspend fun refresh() {
        if (state.busy) return
        state = state.copy(loading = true, error = null)
        try {
            state = state.copy(loading = false, resources = source.resources())
        } catch (cancelled: CancellationException) {
            state = state.copy(loading = false)
            throw cancelled
        } catch (error: Exception) {
            state = state.copy(loading = false, error = error.displayMessage("The inventory could not be loaded. Check the connection and retry."))
        }
    }

    /** Scans the clouds on the server, then shows the new inventory. */
    suspend fun scan() {
        if (state.busy) return
        state = state.copy(scanning = true, error = null)
        try {
            val summary = source.scan()
            state = state.copy(scanning = false, lastScan = summary, resources = source.resources())
        } catch (cancelled: CancellationException) {
            state = state.copy(scanning = false)
            throw cancelled
        } catch (error: Exception) {
            state = state.copy(scanning = false, error = error.displayMessage("The scan did not finish. The stored inventory is unchanged."))
        }
    }
}
