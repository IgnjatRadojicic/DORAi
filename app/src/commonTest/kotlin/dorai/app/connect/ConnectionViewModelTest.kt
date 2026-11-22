package dorai.app.connect

import dorai.app.components.runImmediate
import dorai.shared.api.ApiResult
import dorai.shared.model.Health
import dorai.shared.model.Problem
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionViewModelTest {
    @Test
    fun startsAsChecking() {
        val model = ConnectionViewModel("http://localhost:8080") { ApiResult.Success(Health("ok", "DORAi")) }
        assertEquals(ServerStatus.CHECKING, model.status)
    }

    @Test
    fun aDoraiServerThatAnswersIsConnected() {
        val model = ConnectionViewModel("http://localhost:8080") { ApiResult.Success(Health("ok", "DORAi")) }
        runImmediate { model.check() }
        assertEquals(ServerStatus.CONNECTED, model.status)
    }

    @Test
    fun noAnswerIsNotConnected() {
        val model = ConnectionViewModel("http://localhost:8080") { ApiResult.Failure(Problem(title = "Network error", status = 0)) }
        runImmediate { model.check() }
        assertEquals(ServerStatus.UNREACHABLE, model.status)
    }

    @Test
    fun anotherServiceOnTheAddressIsNotConnected() {
        val model = ConnectionViewModel("http://localhost:8080") { ApiResult.Success(Health("ok", "something else")) }
        runImmediate { model.check() }
        assertEquals(ServerStatus.UNREACHABLE, model.status)
    }

    @Test
    fun theMarkFollowsTheServerWhenItStopsAndStartsAgain() {
        var up = true
        val model = ConnectionViewModel("http://localhost:8080") {
            if (up) ApiResult.Success(Health("ok", "DORAi")) else ApiResult.Failure(Problem(title = "x", status = 0))
        }
        runImmediate { model.check() }
        assertEquals(ServerStatus.CONNECTED, model.status)
        up = false
        runImmediate { model.check() }
        assertEquals(ServerStatus.UNREACHABLE, model.status)
        up = true
        runImmediate { model.check() }
        assertEquals(ServerStatus.CONNECTED, model.status)
    }

    @Test
    fun aCheckCancelledByASwitchedServerKeepsWatching() {
        var calls = 0
        val model = ConnectionViewModel("http://localhost:8080") {
            calls++
            if (calls == 1) throw kotlinx.coroutines.CancellationException("client closed") else ApiResult.Success(Health("ok", "DORAi"))
        }
        runImmediate { model.check() }
        assertEquals(ServerStatus.CHECKING, model.status)
        runImmediate { model.check() }
        assertEquals(ServerStatus.CONNECTED, model.status)
    }
}
