package dorai.app.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dorai.app.auth.LoginGateway
import dorai.app.auth.SavedTokenStore
import dorai.app.auth.LoginViewModel
import dorai.app.components.AppLanguage
import dorai.app.components.WorkspaceSection
import dorai.app.components.matijaSections
import dorai.app.connect.ConnectionViewModel
import dorai.app.connect.InviteGateway
import dorai.app.connect.InviteViewModel
import dorai.app.connect.SavedServer
import dorai.app.connect.SavedSettings
import dorai.app.connect.ServerLink
import dorai.app.connect.ServerProbe
import dorai.app.connect.defaultServerAddress
import dorai.app.findings.FindingsViewModel
import dorai.app.inventory.InventoryViewModel
import dorai.app.review.ReviewViewModel
import dorai.app.theme.ThemeMode
import dorai.app.validation.ValidationViewModel
import dorai.app.roi.RoiViewModel
import dorai.app.admin.AdminViewModel
import dorai.app.register.RegisterViewModel
import dorai.shared.api.DoraiApi
import dorai.shared.api.TokenStore
import dorai.shared.model.Role
import dorai.shared.model.Tokens
import kotlin.time.Clock
import kotlin.time.Instant

object Sections {
    const val INVENTORY = "inventory"
    const val REVIEW = "review"
    const val FINDINGS = "findings"
    const val EXPORT = "export"
    const val ROI = "roi"
    const val REGISTER = "register"
    const val ADMIN = "admin"
}

/**
 * The workspace sections in Matija's frame: Luka's 01 and 02, Matija's 03 and 04, the RoI (05),
 * the register items (06) and, for admins only, the administration (07).
 */
val shellSections: List<WorkspaceSection> = listOf(
    WorkspaceSection(Sections.INVENTORY, "Cloud inventory", "01"),
    WorkspaceSection(Sections.REVIEW, "AI review", "02"),
) + matijaSections + listOf(
    WorkspaceSection(Sections.ROI, "Register of Information", "05"),
    WorkspaceSection(Sections.REGISTER, "Register items", "06"),
    WorkspaceSection(Sections.ADMIN, "Administration", "07"),
)

/** The sections [role] sees: the administration only for admins. */
fun sectionsFor(role: Role): List<WorkspaceSection> = shellSections.filter { it.id != Sections.ADMIN || role == Role.Admin }

data class ShellState(
    val section: String = Sections.INVENTORY,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.ENGLISH,
)

/** Everything that belongs to one signed-in user. Dropped on logout, so results never carry over. */
class Workspace(val session: Tokens, api: DoraiApi) {
    val sources = ServerSources(api)
    val inventory = InventoryViewModel(sources.inventory)
    val review = ReviewViewModel(sources.review)
    val findings = FindingsViewModel(sources.findings)
    val validation = ValidationViewModel(sources.validation)
    val roi = RoiViewModel(sources.roi, canEdit = { session.role >= Role.Reviewer })
    val register = RegisterViewModel(sources.register, canEdit = { session.role >= Role.Reviewer })
    val admin = AdminViewModel(sources.admin, me = session.username)
    val sections: List<WorkspaceSection> = sectionsFor(session.role)

    /** The app only hides what the role can't use; the server checks every call. */
    val canReview: Boolean get() = session.role >= Role.Reviewer
}

/**
 * Root state of the app: server, connection, login, section, theme and language.
 * [restore] puts back what [storage] kept from the last run, including a session that is still valid;
 * until then the server is [address] (the development address). An invite link changes it.
 * Tests replace [openApi] and the server calls ([probe], [gateway], [invites]); by default they go to the current server.
 */
class ShellViewModel(
    address: String = defaultServerAddress,
    private val storage: AppStorage = AppStorage.inMemory(),
    private val openApi: (ServerLink, TokenStore) -> DoraiApi =
        { server, tokens -> DoraiApi(server.address, tokens = tokens, fingerprint = server.fingerprint) },
    probe: ServerProbe? = null,
    gateway: LoginGateway? = null,
    invites: InviteGateway? = null,
) : ViewModel() {
    var state by mutableStateOf(ShellState())
        private set

    /** The signed-in workspace, or null while the login page is shown. */
    var workspace by mutableStateOf<Workspace?>(null)
        private set

    /** False until [restore] has run; the app shows neither login nor workspace before. */
    var ready by mutableStateOf(false)
        private set

    private var server = ServerLink(address)
    private var saved by mutableStateOf(SavedSettings())

    /** The servers to switch between on the login page, the one in use included. */
    val servers: List<SavedServer> get() = saved.servers

    /** The address in use, to mark it on the list. */
    val serverAddress: String get() = server.address

    /** The session of the server in use; each server keeps its own. */
    private var tokens = sessionFor(server.address)

    /**
     * Forgetting the tokens of the server in use always ends on the login page. A logout has already closed
     * the workspace; when it is still open the server refused the refresh, so the login page says the session ended.
     */
    private fun sessionFor(address: String): SavedTokenStore {
        val store = SavedTokenStore(storage.tokens, address)
        store.onCleared = {
            if (store === tokens && workspace != null) {
                workspace = null
                login.sessionEnded()
            }
        }
        return store
    }

    /** Client for the current server; replaced when another server is chosen. */
    private var api: DoraiApi = openApi(server, tokens)

    val connection = ConnectionViewModel(address, probe ?: ServerProbe { api.health() })
    val login = LoginViewModel(gateway ?: object : LoginGateway {
        override suspend fun login(username: String, password: String) = api.login(username, password)
        override suspend fun changePassword(username: String, currentPassword: String, newPassword: String) =
            api.changePassword(username, currentPassword, newPassword)
    })
    val invite = InviteViewModel(invites ?: InviteGateway { api.redeem(it) })

    /**
     * Opens an invite link (pasted on desktop, tapped on Android): switches to its server, adds it to the
     * saved servers, redeems the one-time code and leaves an empty login page. Problems show in the invite field.
     * Other servers keep their sessions; on the invited server the invite is for a new user, so any session
     * there ends.
     */
    suspend fun openInvite(text: String = invite.state.text) {
        if (invite.state.busy) return
        val link = invite.read(text) ?: return
        leave()
        switchServer(ServerLink(link.server, link.fingerprint))
        if (tokens.load() != null) api.logout()
        remember(saved.copy(server = server, username = null, servers = saved.servers.with(SavedServer(server))))
        login.reset()
        invite.redeem(link.code)
        connection.check()
    }

    /**
     * Switches to a saved server without an invite. Its own session opens the workspace straight away
     * while it can still be refreshed; otherwise the login page asks for that server's password.
     */
    suspend fun useServer(next: ServerLink, now: Instant = Clock.System.now()) {
        val entry = saved.servers.firstOrNull { it.link == next } ?: return
        leave()
        switchServer(next)
        remember(saved.copy(server = next, username = entry.username))
        login.reset()
        entry.username?.let(login::onUsernameChange)
        connection.check()
        resume(now)
    }

    /**
     * Takes a server off the list and ends its session there. The server in use stays; switch away first.
     * An unreachable server only loses the session on this device.
     */
    suspend fun forgetServer(link: ServerLink) {
        if (link == server || saved.servers.none { it.link == link }) return
        remember(saved.copy(servers = saved.servers.filter { it.link != link }))
        val store = sessionFor(link.address)
        if (store.load() == null) return
        val other = openApi(link, store)
        try {
            other.logout()
        } finally {
            other.close()
            store.save(null)
        }
    }

    /**
     * Puts back the last run: server, theme, language and username, and opens the workspace straight away
     * when the saved session can still be refreshed. The first call refreshes it if needed; a refused
     * refresh returns to the login page.
     */
    suspend fun restore(now: Instant = Clock.System.now()) {
        if (ready) return
        saved = storage.settings.load()
        saved.server?.let(::switchServer)
        state = state.copy(theme = saved.theme ?: state.theme, language = saved.language ?: state.language)
        saved.username?.let(login::onUsernameChange)
        resume(now)
        ready = true
    }

    /** Opens the workspace with the saved session of the server in use, when it can still be refreshed. */
    private suspend fun resume(now: Instant) {
        val session = tokens.load() ?: return
        if (session.refreshExpiresAt > now) open(session) else tokens.save(null)
    }

    private fun switchServer(next: ServerLink) {
        if (next == server) return
        api.close()
        server = next
        tokens = sessionFor(next.address)
        api = openApi(next, tokens)
        connection.switchTo(next.address)
    }

    /** Adds [entry] to the list, or replaces the entry with the same address in its place. */
    private fun List<SavedServer>.with(entry: SavedServer): List<SavedServer> =
        if (any { it.link.address == entry.link.address }) map { if (it.link.address == entry.link.address) entry else it }
        else this + entry

    /** Saves in the background, only when something changed. */
    private fun remember(next: SavedSettings) {
        if (next == saved) return
        saved = next
        storage.settings.save(next)
    }

    /** Runs the login page and opens the platform when the server accepts it. */
    suspend fun login() {
        login.login()?.let(::open)
    }

    /** Sets the new password after a temporary one and opens the platform with the session it returns. */
    suspend fun changePassword() {
        login.changePassword()?.let(::open)
    }

    private fun open(session: Tokens) {
        val opened = Workspace(session, api)
        workspace = opened
        // The section from the last run, when this role may still see it.
        val section = saved.section?.takeIf { id -> opened.sections.any { it.id == id && it.enabled } } ?: Sections.INVENTORY
        state = state.copy(section = section)
        history.clear()
        remember(saved.copy(server = server, username = session.username,
            servers = saved.servers.with(SavedServer(server, session.username))))
    }

    /** Back to the login page at once, then ends the session on the server. */
    suspend fun logout() {
        if (workspace == null) return
        workspace = null
        api.logout()
    }

    /** Back to the login page to pick another server; the session here stays for switching back. */
    fun leave() {
        workspace = null
    }

    /** The sections visited before the current one, newest last; Back on Android walks it. */
    private val history = mutableStateListOf<String>()

    /** True when [back] has a section to return to; otherwise Back leaves the app. */
    val canGoBack: Boolean get() = history.isNotEmpty()

    fun select(section: String) {
        val allowed = workspace?.sections ?: sectionsFor(Role.Viewer)
        if (section == state.section || allowed.none { it.id == section && it.enabled }) return
        history.remove(section)
        history.add(state.section)
        go(section)
    }

    /** Returns to the previous section. */
    fun back() {
        val previous = history.removeLastOrNull() ?: return
        go(previous)
    }

    private fun go(section: String) {
        state = state.copy(section = section)
        if (workspace != null) remember(saved.copy(section = section))
    }

    fun setTheme(theme: ThemeMode) {
        state = state.copy(theme = theme)
        remember(saved.copy(theme = theme))
    }

    fun setLanguage(language: AppLanguage) {
        state = state.copy(language = language)
        remember(saved.copy(language = language))
    }

    override fun onCleared() = api.close()
}
