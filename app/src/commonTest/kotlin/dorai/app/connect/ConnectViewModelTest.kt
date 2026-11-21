package dorai.app.connect

import dorai.app.components.runImmediate
import dorai.shared.api.ApiResult
import dorai.shared.model.Health
import dorai.shared.model.Problem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ConnectViewModelTest {
    private val healthy = ServerProbe { ApiResult.Success(Health("ok", "DORAi")) }

    private fun connect(model: ConnectViewModel): String? {
        var result: String? = null
        runImmediate { result = model.connect() }
        return result
    }

    @Test
    fun aHealthyServerConnectsWithTheNormalizedAddress() {
        var asked: String? = null
        val model = ConnectViewModel("  http://localhost:8080/api/ ") { address ->
            asked = address
            ApiResult.Success(Health("ok", "DORAi"))
        }
        assertEquals("http://localhost:8080", connect(model))
        assertEquals("http://localhost:8080", asked)
        assertNull(model.state.error)
        assertFalse(model.state.busy)
    }

    @Test
    fun anInvalidAddressIsRejectedWithoutCallingTheServer() {
        var called = false
        val model = ConnectViewModel("localhost:8080") { called = true; ApiResult.Success(Health("ok", "DORAi")) }
        assertNull(connect(model))
        assertFalse(called)
        assertEquals(ConnectViewModel.INVALID_ADDRESS, model.state.error)
    }

    @Test
    fun aNetworkFailureShowsThatTheServerDidNotAnswer() {
        val model = ConnectViewModel("http://10.0.2.2:8080") {
            ApiResult.Failure(Problem(title = "Network error", status = 0, detail = "Connection refused: internal detail"))
        }
        assertNull(connect(model))
        assertEquals(ConnectViewModel.UNREACHABLE, model.state.error)
        assertFalse(model.state.busy)
    }

    @Test
    fun anHttpErrorShowsTheStatusButNotTheServerDetail() {
        val model = ConnectViewModel("http://localhost:8080") {
            ApiResult.Failure(Problem(title = "Service Unavailable", status = 503, detail = "stack trace"))
        }
        assertNull(connect(model))
        assertEquals("The server answered with HTTP 503.", model.state.error)
    }

    @Test
    fun anotherServiceOnTheAddressIsNotAccepted() {
        val model = ConnectViewModel("http://localhost:8080") { ApiResult.Success(Health("ok", "something else")) }
        assertNull(connect(model))
        assertEquals(ConnectViewModel.NOT_DORAI, model.state.error)
    }

    @Test
    fun typingClearsThePreviousError() {
        val model = ConnectViewModel("nope", healthy)
        connect(model)
        model.onAddressChange("http://localhost:8080")
        assertNull(model.state.error)
        assertEquals("http://localhost:8080", connect(model))
    }

    @Test
    fun addressNormalization() {
        assertEquals("https://dorai.firm.local:8443", normalizeServerAddress("https://dorai.firm.local:8443/"))
        assertEquals("http://localhost:8080", normalizeServerAddress("http://localhost:8080/api"))
        assertNull(normalizeServerAddress("ftp://localhost"))
        assertNull(normalizeServerAddress("http://"))
        assertNull(normalizeServerAddress("http://local host:8080"))
    }
}
