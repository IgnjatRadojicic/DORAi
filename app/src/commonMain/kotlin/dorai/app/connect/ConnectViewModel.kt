package dorai.app.connect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dorai.shared.Dorai
import dorai.shared.api.ApiResult
import dorai.shared.model.Health
import kotlinx.coroutines.CancellationException

/** Asks the server at [address] for `/api/health`. The shell backs it with `DoraiApi`, tests with a fake. */
fun interface ServerProbe {
    suspend fun health(address: String): ApiResult<Health>
}

data class ConnectState(
    val address: String,
    val busy: Boolean = false,
    val error: String? = null,
)

class ConnectViewModel(initialAddress: String, private val probe: ServerProbe) : ViewModel() {
    var state by mutableStateOf(ConnectState(initialAddress))
        private set

    fun onAddressChange(address: String) {
        if (!state.busy) state = state.copy(address = address, error = null)
    }

    /** Checks the server. Returns the normalized address when a DORAi server answers, otherwise null with an error in the state. */
    suspend fun connect(): String? {
        if (state.busy) return null
        val address = normalizeServerAddress(state.address)
        if (address == null) {
            state = state.copy(error = INVALID_ADDRESS)
            return null
        }
        state = ConnectState(address, busy = true)
        val error = try {
            when (val result = probe.health(address)) {
                is ApiResult.Success -> if (result.value.app == Dorai.NAME) null else NOT_DORAI
                is ApiResult.Failure -> if (result.problem.status == 0) UNREACHABLE else "The server answered with HTTP ${result.problem.status}."
            }
        } catch (cancelled: CancellationException) {
            state = ConnectState(address)
            throw cancelled
        } catch (unexpected: Exception) {
            UNREACHABLE
        }
        state = ConnectState(address, error = error)
        return if (error == null) address else null
    }

    companion object {
        const val INVALID_ADDRESS = "Enter an address that starts with http:// or https://"
        const val UNREACHABLE = "The server did not answer. Check the address and that the server is running."
        const val NOT_DORAI = "This address answers, but it is not a DORAi server."
    }
}
