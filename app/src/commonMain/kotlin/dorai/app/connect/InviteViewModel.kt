package dorai.app.connect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dorai.shared.Dorai
import dorai.shared.api.ApiResult
import dorai.shared.api.CertificateProblem
import dorai.shared.model.RedeemResponse
import kotlinx.coroutines.CancellationException

/** Redeems an invite code on the current server. The shell backs it with `DoraiApi.redeem`, tests with a fake. */
fun interface InviteGateway {
    suspend fun redeem(code: String): ApiResult<RedeemResponse>
}

data class InviteState(
    /** Whether the "Have an invite link?" field is open on the login page. */
    val open: Boolean = false,
    val text: String = "",
    val busy: Boolean = false,
    val error: String? = null,
) {
    val canUse: Boolean get() = !busy && text.isNotBlank()
}

/** The invite link field on the login page. On success nothing is shown: the login page is simply ready. */
class InviteViewModel(private val gateway: InviteGateway) : ViewModel() {
    var state by mutableStateOf(InviteState())
        private set

    fun toggle() {
        if (!state.busy) state = state.copy(open = !state.open, error = null)
    }

    fun onTextChange(text: String) {
        if (!state.busy) state = state.copy(text = text, error = null)
    }

    /** Reads [text] as an invite link. Returns it when it can be used, otherwise null with the reason in the field. */
    fun read(text: String): InviteLink? = when (val result = parseInviteLink(text)) {
        is InviteLinkResult.Valid -> result.link
        is InviteLinkResult.Invalid -> { fail(result.message, text); null }
    }

    /** Shows [message] in the opened field, keeping the link so the user can see what was used. */
    fun fail(message: String, text: String = state.text) {
        state = InviteState(open = true, text = text, error = message)
    }

    /** Uses the one-time code on the server the app now talks to. True when the server accepted it. */
    suspend fun redeem(code: String): Boolean {
        state = state.copy(busy = true, error = null)
        val error = try {
            when (val result = gateway.redeem(code)) {
                is ApiResult.Success -> if (result.value.app == Dorai.NAME) null else NOT_DORAI
                is ApiResult.Failure -> when {
                    result.problem.type == CertificateProblem.CHANGED -> CERTIFICATE_CHANGED
                    result.problem.status == 0 -> UNREACHABLE
                    // The server writes these for users, for example "Ask your administrator for a new link."
                    result.problem.status == 404 || result.problem.status == 429 -> result.problem.detail ?: USED_OR_EXPIRED
                    else -> "The invite could not be used. The server answered with HTTP ${result.problem.status}."
                }
            }
        } catch (cancelled: CancellationException) {
            state = state.copy(busy = false)
            throw cancelled
        } catch (unexpected: Exception) {
            UNREACHABLE
        }
        if (error == null) state = InviteState() else fail(error)
        return error == null
    }

    companion object {
        const val UNREACHABLE = "The server in this invite link did not answer. Check the connection and try again."
        const val USED_OR_EXPIRED = "This invite link was already used or has expired. Ask your administrator for a new one."
        const val NOT_DORAI = "The server in this invite link is not a DORAi server."
        const val CERTIFICATE_CHANGED = "The server's certificate is not the one in your invite link. Ask your administrator for a new invite link."
    }
}
