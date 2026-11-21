package dorai.app.auth

import dorai.app.components.runImmediate
import dorai.shared.api.ApiResult
import dorai.shared.model.LoginResponse
import dorai.shared.model.Tokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class PasswordChangeTest {
    private val temporary = ApiResult.Success(LoginResponse(passwordChangeRequired = true))

    /** Signs in with a temporary password, so the model is on the password change step. */
    private fun changeStep(gateway: FakeLoginGateway = FakeLoginGateway(loginAnswer = temporary)): LoginViewModel =
        LoginViewModel(gateway).apply {
            onUsernameChange("luka")
            onPasswordChange("Temp-Password-1")
            runImmediate { login() }
        }

    private fun LoginViewModel.changeNow(new: String = "my-new-password", repeat: String = new): Tokens? {
        onNewPasswordChange(new)
        onRepeatPasswordChange(repeat)
        var result: Tokens? = null
        runImmediate { result = changePassword() }
        return result
    }

    @Test
    fun aTemporaryPasswordOpensThePasswordChangeStepInsteadOfThePlatform() {
        var session: Tokens? = testTokens
        val model = LoginViewModel(FakeLoginGateway(loginAnswer = temporary)).apply {
            onUsernameChange("luka")
            onPasswordChange("Temp-Password-1")
        }
        runImmediate { session = model.login() }
        assertNull(session)
        assertEquals(LoginStep.CHANGE_PASSWORD, model.state.step)
        assertEquals("", model.state.password)
        assertNull(model.state.error)
    }

    @Test
    fun theChangeSendsTheTemporaryPasswordAndReturnsTheSession() {
        val gateway = FakeLoginGateway(loginAnswer = temporary)
        val model = changeStep(gateway)
        assertSame(testTokens, model.changeNow())
        assertEquals(Triple("luka", "Temp-Password-1", "my-new-password"), gateway.lastChange)
        assertEquals(LoginStep.SIGN_IN, model.state.step)
        assertEquals("", model.state.newPassword)
    }

    @Test
    fun differentPasswordsAreCaughtBeforeTheServer() {
        val gateway = FakeLoginGateway(loginAnswer = temporary)
        val model = changeStep(gateway)
        assertNull(model.changeNow("my-new-password", "my-new-passwort"))
        assertEquals(LoginViewModel.PASSWORDS_DIFFER, model.state.error)
        assertEquals("", model.state.repeatPassword)
        assertNull(gateway.lastChange)
    }

    @Test
    fun aRuleTheServerRejectsIsShownAndTheStepStays() {
        val gateway = FakeLoginGateway(loginAnswer = temporary, changeAnswer = failure(400, "The password needs at least 12 characters."))
        val model = changeStep(gateway)
        assertNull(model.changeNow("short"))
        assertEquals("The password needs at least 12 characters.", model.state.error)
        assertEquals(LoginStep.CHANGE_PASSWORD, model.state.step)
        assertEquals("", model.state.newPassword)
    }

    @Test
    fun aTemporaryPasswordThatNoLongerWorksGoesBackToSignIn() {
        val model = changeStep(FakeLoginGateway(loginAnswer = temporary, changeAnswer = failure(401)))
        assertNull(model.changeNow())
        assertEquals(LoginStep.SIGN_IN, model.state.step)
        assertEquals(LoginViewModel.SIGN_IN_AGAIN, model.state.error)
    }

    @Test
    fun noAnswerDuringTheChangeKeepsTheStep() {
        val model = changeStep(FakeLoginGateway(loginAnswer = temporary, changeAnswer = failure(0)))
        model.changeNow()
        assertEquals(LoginViewModel.UNREACHABLE, model.state.error)
        assertEquals(LoginStep.CHANGE_PASSWORD, model.state.step)
    }

    @Test
    fun backToSignInForgetsTheTemporaryPassword() {
        val gateway = FakeLoginGateway(loginAnswer = temporary)
        val model = changeStep(gateway)
        model.backToSignIn()
        assertEquals(LoginStep.SIGN_IN, model.state.step)
        assertEquals("luka", model.state.username)
        assertNull(model.changeNow())
        assertNull(gateway.lastChange)
    }
}
