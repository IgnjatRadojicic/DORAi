package dorai.app.connect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dorai.shared.Dorai
import dorai.shared.api.ApiResult
import dorai.shared.model.Health
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class ServerStatus { CHECKING, CONNECTED, UNREACHABLE }

/** Asks the server for `/api/health`. The shell backs it with `DoraiApi`, tests with a fake. */
fun interface ServerProbe {
    suspend fun health(): ApiResult<Health>
}

/** Whether the server at [address] answers, for the connection mark. */
class ConnectionViewModel(address: String, private val probe: ServerProbe) : ViewModel() {
    var address by mutableStateOf(address)
        private set

    var status by mutableStateOf(ServerStatus.CHECKING)
        private set

    /** The app now talks to another server (from an invite link); its state is unknown until the next check. */
    fun switchTo(address: String) {
        this.address = address
        status = ServerStatus.CHECKING
    }

    suspend fun check() {
        status = try {
            when (val result = probe.health()) {
                is ApiResult.Success -> if (result.value.app == Dorai.NAME) ServerStatus.CONNECTED else ServerStatus.UNREACHABLE
                is ApiResult.Failure -> ServerStatus.UNREACHABLE
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unexpected: Exception) {
            ServerStatus.UNREACHABLE
        }
    }

    /** Checks now and then every [interval], until the calling coroutine is cancelled. */
    suspend fun watch(interval: Duration = 10.seconds) {
        while (true) {
            check()
            delay(interval)
        }
    }
}
