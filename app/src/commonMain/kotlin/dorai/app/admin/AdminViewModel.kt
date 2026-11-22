package dorai.app.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dorai.app.components.displayMessage
import dorai.shared.model.CreatedUser
import dorai.shared.model.Invite
import dorai.shared.model.Role
import dorai.shared.model.User
import kotlinx.coroutines.CancellationException

/** The admin calls of the server; only an admin reaches them (the server checks every call). */
interface AdminSource {
    suspend fun users(): List<User>
    suspend fun invites(): List<Invite>
    suspend fun createUser(username: String, role: Role, temporaryPassword: String?): CreatedUser
    suspend fun disable(username: String)
    suspend fun revoke(username: String)
    suspend fun invite(username: String): Invite
}

/** An action that waits for a second click, because it locks a person out. */
data class PendingConfirm(val action: AdminAction, val username: String)

enum class AdminAction { DISABLE, REVOKE }

/** What to hand over after a new user or invite: shown once, never stored by the app. */
data class Handover(val username: String, val temporaryPassword: String?, val link: String?, val expiresAt: String)

data class AdminState(
    val busy: Boolean = false,
    val users: List<User> = emptyList(),
    val invites: List<Invite> = emptyList(),
    val newUsername: String = "",
    val newRole: Role = Role.Reviewer,
    val newPassword: String = "",
    val confirm: PendingConfirm? = null,
    val handover: Handover? = null,
    val message: String? = null,
    val error: String? = null,
) {
    val canCreate: Boolean get() = !busy && USERNAME.matches(newUsername.trim())

    /** Invites not yet used, newest first. */
    val openInvites: List<Invite> get() = invites.filter { it.redeemedAt == null }.sortedByDescending { it.expiresAt }

    companion object {
        /** The server's rule (Usernames.normalize): 3 to 64 characters, starting with a letter or digit. */
        val USERNAME = Regex("^[a-z0-9][a-z0-9._-]{2,63}$")
    }
}

/**
 * Users and invites for the admin. A new user gets a temporary password (typed by the admin, or made by the
 * server when left blank) and an invite link; both are shown once, to be handed over on separate channels.
 * Disabling and signing out everywhere ask for a second click.
 */
class AdminViewModel(private val source: AdminSource, private val me: String) : ViewModel() {
    var state by mutableStateOf(AdminState())
        private set

    suspend fun refresh() {
        if (state.busy) return
        state = state.copy(busy = true, error = null)
        state = try {
            state.copy(busy = false, users = source.users().sortedBy { it.username }, invites = source.invites())
        } catch (cancelled: CancellationException) {
            state = state.copy(busy = false)
            throw cancelled
        } catch (error: Exception) {
            state.copy(busy = false, error = error.displayMessage("Users could not be loaded. Check the connection and retry."))
        }
    }

    fun onUsername(text: String) { state = state.copy(newUsername = text.lowercase(), message = null) }
    fun onRole(role: Role) { state = state.copy(newRole = role) }
    fun onPassword(text: String) { state = state.copy(newPassword = text) }
    fun closeHandover() { state = state.copy(handover = null) }
    fun cancelConfirm() { state = state.copy(confirm = null) }

    /** The admin's own account can't be disabled or signed out from here, so they never lock themselves out. */
    fun canActOn(user: User): Boolean = user.username != me && !user.disabled

    suspend fun create() {
        if (!state.canCreate) {
            state = state.copy(message = "A username has 3 to 64 characters: letters, digits, dot, dash or underscore, starting with a letter or digit.")
            return
        }
        run("The user could not be created.") {
            val created = source.createUser(state.newUsername.trim(), state.newRole, state.newPassword.ifBlank { null })
            state = state.copy(
                newUsername = "", newPassword = "",
                handover = Handover(created.user.username, created.temporaryPassword, created.invite.link, created.invite.expiresAt.toString()),
                message = "Created ${created.user.username}.",
            )
        }
    }

    suspend fun invite(username: String) = run("The invite could not be created.") {
        val invite = source.invite(username)
        state = state.copy(handover = Handover(username, null, invite.link, invite.expiresAt.toString()), message = "New invite for $username.")
    }

    /** First click asks, second click acts. */
    suspend fun act(action: AdminAction, username: String) {
        val user = state.users.firstOrNull { it.username == username } ?: return
        if (!canActOn(user)) return
        if (state.confirm != PendingConfirm(action, username)) {
            state = state.copy(confirm = PendingConfirm(action, username), message = null)
            return
        }
        run(if (action == AdminAction.DISABLE) "The user could not be disabled." else "The sessions could not be ended.") {
            when (action) {
                AdminAction.DISABLE -> source.disable(username)
                AdminAction.REVOKE -> source.revoke(username)
            }
            state = state.copy(confirm = null, message = if (action == AdminAction.DISABLE) "$username is disabled." else "$username is signed out everywhere.")
        }
    }

    private suspend fun run(fallback: String, block: suspend () -> Unit) {
        if (state.busy) return
        state = state.copy(busy = true, error = null)
        try {
            block()
            state = state.copy(busy = false, users = source.users().sortedBy { it.username }, invites = source.invites())
        } catch (cancelled: CancellationException) {
            state = state.copy(busy = false)
            throw cancelled
        } catch (error: Exception) {
            state = state.copy(busy = false, confirm = null, message = error.displayMessage(fallback))
        }
    }
}
