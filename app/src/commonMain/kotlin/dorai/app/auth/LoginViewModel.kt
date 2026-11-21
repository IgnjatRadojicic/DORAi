package dorai.app.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dorai.shared.api.ApiResult
import dorai.shared.model.LoginResponse
import dorai.shared.model.Problem
import dorai.shared.model.Tokens
import kotlinx.coroutines.CancellationException

/** Sends the login to the server. The shell backs it with `DoraiApi.login`, tests with a fake. */
fun interface LoginGateway {
    suspend fun login(username: String, password: String): ApiResult<LoginResponse>
}

data class LoginState(
    val username: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = !busy && username.isNotBlank() && password.isNotEmpty()
}

class LoginViewModel(private val gateway: LoginGateway) : ViewModel() {
    var state by mutableStateOf(LoginState())
        private set

    fun onUsernameChange(username: String) {
        if (!state.busy) state = state.copy(username = username, error = null)
    }

    fun onPasswordChange(password: String) {
        if (!state.busy) state = state.copy(password = password, error = null)
    }

    /** Logs in. Returns the session on success, otherwise null with a message in the state. */
    suspend fun login(): Tokens? {
        if (!state.canSubmit) return null
        state = state.copy(busy = true, error = null)
        var tokens: Tokens? = null
        val error = try {
            when (val result = gateway.login(state.username.trim(), state.password)) {
                is ApiResult.Success -> when {
                    result.value.passwordChangeRequired -> PASSWORD_CHANGE_REQUIRED
                    result.value.tokens == null -> UNEXPECTED
                    else -> { tokens = result.value.tokens; null }
                }
                is ApiResult.Failure -> result.problem.toLoginMessage()
            }
        } catch (cancelled: CancellationException) {
            state = state.copy(busy = false)
            throw cancelled
        } catch (unexpected: Exception) {
            UNREACHABLE
        }
        // The password is never kept after an attempt; the username stays for the next one.
        state = LoginState(username = state.username, error = error)
        return tokens
    }

    private fun Problem.toLoginMessage(): String = when (status) {
        0 -> UNREACHABLE
        // The server writes these messages for users, for example the remaining lockout time.
        401 -> detail ?: WRONG_CREDENTIALS
        429 -> detail ?: TOO_MANY_ATTEMPTS
        404 -> NO_SIGN_IN
        else -> "Sign-in failed. The server answered with HTTP $status."
    }

    companion object {
        const val UNREACHABLE = "The server did not answer. Check the connection and try again."
        const val WRONG_CREDENTIALS = "Wrong username or password."
        const val TOO_MANY_ATTEMPTS = "Too many attempts. Wait a moment and try again."
        const val NO_SIGN_IN = "Sign-in is not available on this server."
        const val PASSWORD_CHANGE_REQUIRED = "Your password is temporary and must be changed before you can sign in."
        const val UNEXPECTED = "Sign-in failed. Try again."
    }
}
