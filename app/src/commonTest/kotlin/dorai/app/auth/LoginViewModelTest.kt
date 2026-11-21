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
class LoginViewModelTest {
    private val tokens = Tokens(
        accessToken = "access", accessExpiresAt = Instant.fromEpochSeconds(0),
        refreshToken = "refresh", refreshExpiresAt = Instant.fromEpochSeconds(0),
        username = "luka", role = Role.Reviewer,
    )

    private fun model(answer: (String, String) -> ApiResult<LoginResponse>) =
        LoginViewModel { username, password -> answer(username, password) }.apply {
            onUsernameChange(" luka ")
            onPasswordChange("correct horse battery")
        }

    private fun LoginViewModel.loginNow(): Tokens? {
        var result: Tokens? = null
        runImmediate { result = login() }
        return result
    }

    private fun failure(status: Int, detail: String? = null) =
        ApiResult.Failure(Problem(title = "x", status = status, detail = detail))

    @Test
    fun rightCredentialsReturnTheSessionAndForgetThePassword() {
        var sent: Pair<String, String>? = null
        val model = model { u, p -> sent = u to p; ApiResult.Success(LoginResponse(false, tokens)) }
        assertSame(tokens, model.loginNow())
        assertEquals("luka" to "correct horse battery", sent)
        assertEquals("", model.state.password)
        assertNull(model.state.error)
        assertFalse(model.state.busy)
    }

    @Test
    fun wrongCredentialsShowTheServerMessage() {
        val model = model { _, _ -> failure(401, "Wrong username or password.") }
        assertNull(model.loginNow())
        assertEquals("Wrong username or password.", model.state.error)
        assertEquals("", model.state.password)
        assertEquals(" luka ", model.state.username)
    }

    @Test
    fun aLockoutShowsTheWaitFromTheServer() {
        val model = model { _, _ -> failure(429, "Too many failed logins. Try again in 15 minutes.") }
        model.loginNow()
        assertEquals("Too many failed logins. Try again in 15 minutes.", model.state.error)
    }

    @Test
    fun aRateLimitWithoutDetailShowsAGeneralWait() {
        val model = model { _, _ -> failure(429) }
        model.loginNow()
        assertEquals(LoginViewModel.TOO_MANY_ATTEMPTS, model.state.error)
    }

    @Test
    fun aServerThatDoesNotAnswerIsReported() {
        val model = model { _, _ -> failure(0, "Connection refused") }
        model.loginNow()
        assertEquals(LoginViewModel.UNREACHABLE, model.state.error)
    }

    @Test
    fun aServerWithoutSignInIsReported() {
        val model = model { _, _ -> failure(404) }
        model.loginNow()
        assertEquals(LoginViewModel.NO_SIGN_IN, model.state.error)
    }

    @Test
    fun otherServerErrorsShowOnlyTheStatus() {
        val model = model { _, _ -> failure(500, "stack trace") }
        model.loginNow()
        assertEquals("Sign-in failed. The server answered with HTTP 500.", model.state.error)
    }

    @Test
    fun aTemporaryPasswordDoesNotOpenThePlatform() {
        val model = model { _, _ -> ApiResult.Success(LoginResponse(passwordChangeRequired = true)) }
        assertNull(model.loginNow())
        assertEquals(LoginViewModel.PASSWORD_CHANGE_REQUIRED, model.state.error)
    }

    @Test
    fun emptyFieldsDoNotCallTheServer() {
        var called = false
        val model = LoginViewModel { _, _ -> called = true; ApiResult.Success(LoginResponse(false, tokens)) }
        model.onUsernameChange("luka")
        assertFalse(model.state.canSubmit)
        assertNull(model.loginNow())
        assertFalse(called)
    }

    @Test
    fun typingClearsTheError() {
        val model = model { _, _ -> failure(401) }
        model.loginNow()
        model.onPasswordChange("x")
        assertNull(model.state.error)
    }
}
