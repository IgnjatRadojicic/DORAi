package dorai.app.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dorai.app.connect.InviteViewModel
import dorai.shared.api.ApiResult
import dorai.shared.api.CertificateProblem
import dorai.shared.model.LoginResponse
import dorai.shared.model.Problem
import dorai.shared.model.Tokens
import kotlinx.coroutines.CancellationException

/** Login calls to the server. The shell backs them with `DoraiApi`, tests with a fake. */
interface LoginGateway {
    suspend fun login(username: String, password: String): ApiResult<LoginResponse>
    suspend fun changePassword(username: String, currentPassword: String, newPassword: String): ApiResult<Tokens>
}

enum class LoginStep { SIGN_IN, CHANGE_PASSWORD }

data class LoginState(
    val step: LoginStep = LoginStep.SIGN_IN,
    val username: String = "",
    val password: String = "",
    val newPassword: String = "",
    val repeatPassword: String = "",
    val busy: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = !busy && username.isNotBlank() && password.isNotEmpty()
    val canChange: Boolean get() = !busy && newPassword.isNotEmpty() && repeatPassword.isNotEmpty()
}

class LoginViewModel(private val gateway: LoginGateway) : ViewModel() {
    var state by mutableStateOf(LoginState())
        private set

    /** The temporary password from the last login, kept only until the change succeeds or the user goes back. */
    private var temporaryPassword: String? = null

    fun onUsernameChange(username: String) {
        if (!state.busy) state = state.copy(username = username, error = null)
    }

    fun onPasswordChange(password: String) {
        if (!state.busy) state = state.copy(password = password, error = null)
    }

    fun onNewPasswordChange(password: String) {
        if (!state.busy) state = state.copy(newPassword = password, error = null)
    }

    fun onRepeatPasswordChange(password: String) {
        if (!state.busy) state = state.copy(repeatPassword = password, error = null)
    }

    /** Empty sign-in card, for example after an invite link switched the server. */
    fun reset() {
        if (state.busy) return
        temporaryPassword = null
        state = LoginState()
    }

    /** The server ended the session (a refused refresh): sign-in card with the username kept and a note why. */
    fun sessionEnded() {
        temporaryPassword = null
        state = LoginState(username = state.username, error = SESSION_ENDED)
    }

    /** Leaves the password change and returns to an empty sign-in card. */
    fun backToSignIn() {
        if (state.busy) return
        temporaryPassword = null
        state = LoginState(username = state.username)
    }

    /**
     * Logs in. Returns the session on success. A temporary password moves to the password change step;
     * otherwise null with a message in the state.
     */
    suspend fun login(): Tokens? {
        if (!state.canSubmit) return null
        val username = state.username.trim()
        val password = state.password
        state = state.copy(busy = true, error = null)
        var tokens: Tokens? = null
        val error = request {
            when (val result = gateway.login(username, password)) {
                is ApiResult.Success -> when {
                    result.value.passwordChangeRequired -> {
                        temporaryPassword = password
                        state = LoginState(step = LoginStep.CHANGE_PASSWORD, username = state.username)
                        return null
                    }
                    result.value.tokens == null -> UNEXPECTED
                    else -> { tokens = result.value.tokens; null }
                }
                is ApiResult.Failure -> result.problem.toMessage(WRONG_CREDENTIALS)
            }
        }
        // The password is never kept after an attempt; the username stays for the next one.
        state = LoginState(username = state.username, error = error)
        return tokens
    }

    /** Sets the new password. Returns the session on success, otherwise null with a message in the state. */
    suspend fun changePassword(): Tokens? {
        val current = temporaryPassword ?: return null
        if (!state.canChange) return null
        if (state.newPassword != state.repeatPassword) {
            state = state.copy(repeatPassword = "", error = PASSWORDS_DIFFER)
            return null
        }
        state = state.copy(busy = true, error = null)
        var tokens: Tokens? = null
        val error = request {
            when (val result = gateway.changePassword(state.username.trim(), current, state.newPassword)) {
                is ApiResult.Success -> { tokens = result.value; null }
                is ApiResult.Failure -> {
                    if (result.problem.status == 401) {
                        // The temporary password no longer works: start again from the sign-in card.
                        temporaryPassword = null
                        state = LoginState(username = state.username, error = SIGN_IN_AGAIN)
                        return null
                    }
                    result.problem.toMessage(SIGN_IN_AGAIN)
                }
            }
        }
        if (tokens != null) {
            temporaryPassword = null
            state = LoginState(username = state.username)
        } else {
            state = state.copy(newPassword = "", repeatPassword = "", busy = false, error = error)
        }
        return tokens
    }

    /** Runs a server call; a failure that is not an API answer counts as no answer. */
    private inline fun request(call: () -> String?): String? = try {
        call()
    } catch (cancelled: CancellationException) {
        state = state.copy(busy = false)
        throw cancelled
    } catch (unexpected: Exception) {
        UNREACHABLE
    }

    private fun Problem.toMessage(unauthorized: String): String = if (type == CertificateProblem.CHANGED) {
        InviteViewModel.CERTIFICATE_CHANGED
    } else when (status) {
        0 -> UNREACHABLE
        // The server writes these for users: wrong password, lockout time, or which password rule failed.
        400 -> detail ?: "The new password was not accepted."
        401 -> detail ?: unauthorized
        429 -> detail ?: TOO_MANY_ATTEMPTS
        404 -> NO_SIGN_IN
        else -> "Sign-in failed. The server answered with HTTP $status."
    }

    companion object {
        const val UNREACHABLE = "The server did not answer. Check the connection and try again."
        const val WRONG_CREDENTIALS = "Wrong username or password."
        const val TOO_MANY_ATTEMPTS = "Too many attempts. Wait a moment and try again."
        const val NO_SIGN_IN = "Sign-in is not available on this server."
        const val PASSWORDS_DIFFER = "The two passwords do not match."
        const val SIGN_IN_AGAIN = "Your temporary password is no longer valid. Sign in again."
        const val UNEXPECTED = "Sign-in failed. Try again."
        const val SESSION_ENDED = "Your session has ended. Sign in again."
    }
}
