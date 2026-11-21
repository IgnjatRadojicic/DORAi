package dorai.app.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dorai.app.auth.LoginGateway
import dorai.app.auth.LoginViewModel
import dorai.app.auth.MemoryVault
import dorai.app.auth.SavedSession
import dorai.app.auth.SessionStore
import dorai.app.components.AppLanguage
import dorai.app.components.WorkspaceSection
import dorai.app.components.matijaSections
import dorai.app.connect.ConnectionViewModel
import dorai.app.connect.InviteGateway
import dorai.app.connect.InviteViewModel
import dorai.app.connect.ServerProbe
import dorai.app.connect.defaultServerAddress
import dorai.app.findings.FindingsViewModel
import dorai.app.inventory.InventoryViewModel
import dorai.app.review.ReviewViewModel
import dorai.app.theme.ThemeMode
import dorai.app.validation.ValidationViewModel
import dorai.shared.api.DoraiApi
import dorai.shared.api.TokenStore
import dorai.shared.model.Role
import dorai.shared.model.Tokens
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object Sections {
    const val INVENTORY = "inventory"
    const val REVIEW = "review"
    const val FINDINGS = "findings"
    const val EXPORT = "export"
}

/** The four workspace sections in Matija's frame: Luka's 01 and 02, then Matija's 03 and 04. */
val shellSections: List<WorkspaceSection> = listOf(
    WorkspaceSection(Sections.INVENTORY, "Cloud inventory", "01"),
    WorkspaceSection(Sections.REVIEW, "AI review", "02"),
) + matijaSections

data class ShellState(
    val section: String = Sections.INVENTORY,
    val theme: ThemeMode = ThemeMode.DARK,
    val language: AppLanguage = AppLanguage.ENGLISH,
)

/** Everything that belongs to one signed-in user. Dropped on logout, so results never carry over. */
class Workspace(val session: Tokens, api: DoraiApi) {
    val sources = ServerSources(api)
    val inventory = InventoryViewModel(sources.inventory)
    val review = ReviewViewModel(sources.review)
    val findings = FindingsViewModel(sources.findings)
    val validation = ValidationViewModel(sources.validation)

    /** The app only hides what the role can't use; the server checks every call. */
    val canReview: Boolean get() = session.role >= Role.Reviewer
}

/** A server the app talks to: its address and, for https, the SHA-256 of its certificate from the invite link. */
data class ServerTarget(val address: String, val fingerprint: String? = null)

/**
 * Certificate pinning (shared lane, Ignjat): set to true once [DoraiApi] trusts exactly the certificate with
 * [ServerTarget.fingerprint], and pass the fingerprint in [openServer]. Until then https invite links are refused
 * instead of trusting any certificate.
 */
const val PINNING_AVAILABLE = false

/** The one place that creates the API client for a server. */
fun openServer(target: ServerTarget, tokens: TokenStore): DoraiApi =
    // Pinning: hand target.fingerprint to DoraiApi here once it can trust only that certificate.
    DoraiApi(target.address, tokens = tokens)

/**
 * Root state of the app: server, connection, login, section, theme and language.
 * The server and the login are kept in [sessions] (encrypted by the platform); theme and language in memory.
 * The server starts as the development address, then the saved one, and changes when an invite link is opened.
 * Tests replace [openApi] and the server calls ([probe], [gateway], [invites]); by default they go to the current server.
 */
@OptIn(ExperimentalTime::class)
class ShellViewModel(
    address: String = defaultServerAddress,
    private val sessions: SessionStore = SessionStore(MemoryVault()),
    private val openApi: (ServerTarget, TokenStore) -> DoraiApi = ::openServer,
    probe: ServerProbe? = null,
    gateway: LoginGateway? = null,
    invites: InviteGateway? = null,
    private val now: () -> Instant = { Clock.System.now() },
) : ViewModel() {
    var state by mutableStateOf(ShellState())
        private set

    /** The signed-in workspace, or null while the login page is shown. */
    var workspace by mutableStateOf<Workspace?>(null)
        private set

    private var target = ServerTarget(address)

    /** Login tokens of the current server; every change is saved with the server. */
    private var tokens = SessionTokens(target, saved = null)

    /** Client for the current server; replaced when an invite link or the saved session names another one. */
    private var api: DoraiApi = openApi(target, tokens)

    val connection = ConnectionViewModel(address, probe ?: ServerProbe { api.health() })
    private val gateway: LoginGateway = gateway ?: object : LoginGateway {
        override suspend fun login(username: String, password: String) = api.login(username, password)
        override suspend fun changePassword(username: String, currentPassword: String, newPassword: String) =
            api.changePassword(username, currentPassword, newPassword)
        override suspend fun logout() { api.logout() }
    }
    val login = LoginViewModel(this.gateway)
    val invite = InviteViewModel(invites ?: InviteGateway { api.redeem(it) })

    private var restored = false

    /**
     * Called once at start: returns to the saved server and, when its login has not expired, opens the platform.
     * A saved https server waits for certificate pinning like an invite link does.
     */
    suspend fun restore() {
        if (restored) return
        restored = true
        val saved = sessions.load() ?: return
        if (saved.fingerprint != null && !PINNING_AVAILABLE) return
        switchServer(ServerTarget(saved.server, saved.fingerprint), saved.tokens)
        val session = saved.tokens ?: return
        if (session.refreshExpiresAt <= now()) tokens.save(null) else open(session)
    }

    /**
     * Opens an invite link (pasted on desktop, tapped on Android): switches to its server, redeems the
     * one-time code and leaves an empty login page. Problems show in the invite field.
     */
    suspend fun openInvite(text: String = invite.state.text) {
        if (invite.state.busy) return
        val link = invite.read(text) ?: return
        if (link.fingerprint != null && !PINNING_AVAILABLE) {
            invite.fail(InviteViewModel.SECURE_NOT_READY, text)
            return
        }
        logout()
        val next = ServerTarget(link.server, link.fingerprint)
        if (next != target) switchServer(next, saved = null)
        sessions.save(SavedSession(next.address, next.fingerprint, null))
        login.reset()
        invite.redeem(link.code)
        connection.check()
    }

    private fun switchServer(next: ServerTarget, saved: Tokens?) {
        api.close()
        target = next
        tokens = SessionTokens(next, saved)
        api = openApi(next, tokens)
        connection.switchTo(next.address)
    }

    /** Runs the login page and opens the platform when the server accepts it. */
    suspend fun login() {
        login.login()?.let { open(it) }
    }

    /** Sets the new password after a temporary one and opens the platform with the session it returns. */
    suspend fun changePassword() {
        login.changePassword()?.let { open(it) }
    }

    private suspend fun open(session: Tokens) {
        tokens.save(session)
        workspace = Workspace(session, api)
        state = state.copy(section = Sections.INVENTORY)
    }

    /** Back to the login page at once, then ends the session on the server and forgets the login (the server stays). */
    suspend fun logout() {
        if (workspace == null) return
        workspace = null
        gateway.logout()
        tokens.save(null)
    }

    /**
     * Tokens of one server, saved with that server in [sessions] on every change: login, refresh, logout.
     * The client clears them when the server refuses a refresh; then the session has ended.
     */
    private inner class SessionTokens(private val server: ServerTarget, saved: Tokens?) : TokenStore {
        private var current: Tokens? = saved

        override suspend fun load(): Tokens? = current

        override suspend fun save(tokens: Tokens?) {
            current = tokens
            sessions.save(SavedSession(server.address, server.fingerprint, tokens))
            if (tokens == null && this === this@ShellViewModel.tokens && workspace != null) {
                workspace = null
                login.showError(LoginViewModel.SESSION_ENDED)
            }
        }
    }

    fun select(section: String) {
        if (shellSections.any { it.id == section && it.enabled }) state = state.copy(section = section)
    }

    fun setTheme(theme: ThemeMode) { state = state.copy(theme = theme) }

    fun setLanguage(language: AppLanguage) { state = state.copy(language = language) }

    override fun onCleared() = api.close()
}
