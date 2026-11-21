package dorai.app.auth

import dorai.app.components.runImmediate
import dorai.shared.api.ApiResult
import dorai.shared.model.LoginResponse
import dorai.shared.model.Problem
import dorai.shared.model.Role
import dorai.shared.model.Tokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
val testTokens = Tokens(
    accessToken = "access", accessExpiresAt = Instant.fromEpochSeconds(0),
    refreshToken = "refresh", refreshExpiresAt = Instant.fromEpochSeconds(0),
    username = "luka", role = Role.Reviewer,
)

/** Answers like the server would; records what the app sent. */
class FakeLoginGateway(
    var loginAnswer: ApiResult<LoginResponse> = ApiResult.Success(LoginResponse(false, testTokens)),
    var changeAnswer: ApiResult<Tokens> = ApiResult.Success(testTokens),
) : LoginGateway {
    var lastLogin: Pair<String, String>? = null
    var lastChange: Triple<String, String, String>? = null

    override suspend fun login(username: String, password: String): ApiResult<LoginResponse> {
        lastLogin = username to password
        return loginAnswer
    }

    override suspend fun changePassword(username: String, currentPassword: String, newPassword: String): ApiResult<Tokens> {
        lastChange = Triple(username, currentPassword, newPassword)
        return changeAnswer
    }
}

fun failure(status: Int, detail: String? = null) = ApiResult.Failure(Problem(title = "x", status = status, detail = detail))

class LoginViewModelTest {
    private fun model(gateway: FakeLoginGateway) = LoginViewModel(gateway).apply {
        onUsernameChange(" luka ")
        onPasswordChange("correct horse battery")
    }

    private fun LoginViewModel.loginNow(): Tokens? {
        var result: Tokens? = null
        runImmediate { result = login() }
        return result
    }

    @Test
    fun rightCredentialsReturnTheSessionAndForgetThePassword() {
        val gateway = FakeLoginGateway()
        val model = model(gateway)
        assertSame(testTokens, model.loginNow())
        assertEquals("luka" to "correct horse battery", gateway.lastLogin)
        assertEquals("", model.state.password)
        assertNull(model.state.error)
        assertFalse(model.state.busy)
    }

    @Test
    fun wrongCredentialsShowTheServerMessage() {
        val model = model(FakeLoginGateway(loginAnswer = failure(401, "Wrong username or password.")))
        assertNull(model.loginNow())
        assertEquals("Wrong username or password.", model.state.error)
        assertEquals("", model.state.password)
        assertEquals(" luka ", model.state.username)
    }

    @Test
    fun aLockoutShowsTheWaitFromTheServer() {
        val model = model(FakeLoginGateway(loginAnswer = failure(429, "Too many failed logins. Try again in 15 minutes.")))
        model.loginNow()
        assertEquals("Too many failed logins. Try again in 15 minutes.", model.state.error)
    }

    @Test
    fun aRateLimitWithoutDetailShowsAGeneralWait() {
        val model = model(FakeLoginGateway(loginAnswer = failure(429)))
        model.loginNow()
        assertEquals(LoginViewModel.TOO_MANY_ATTEMPTS, model.state.error)
    }

    @Test
    fun aServerThatDoesNotAnswerIsReported() {
        val model = model(FakeLoginGateway(loginAnswer = failure(0, "Connection refused")))
        model.loginNow()
        assertEquals(LoginViewModel.UNREACHABLE, model.state.error)
    }

    @Test
    fun aServerWithoutSignInIsReported() {
        val model = model(FakeLoginGateway(loginAnswer = failure(404)))
        model.loginNow()
        assertEquals(LoginViewModel.NO_SIGN_IN, model.state.error)
    }

    @Test
    fun otherServerErrorsShowOnlyTheStatus() {
        val model = model(FakeLoginGateway(loginAnswer = failure(500, "stack trace")))
        model.loginNow()
        assertEquals("Sign-in failed. The server answered with HTTP 500.", model.state.error)
    }

    @Test
    fun emptyFieldsDoNotCallTheServer() {
        val gateway = FakeLoginGateway()
        val model = LoginViewModel(gateway)
        model.onUsernameChange("luka")
        assertFalse(model.state.canSubmit)
        assertNull(model.loginNow())
        assertNull(gateway.lastLogin)
    }

    @Test
    fun typingClearsTheError() {
        val model = model(FakeLoginGateway(loginAnswer = failure(401)))
        model.loginNow()
        model.onPasswordChange("x")
        assertNull(model.state.error)
    }
}
