package dorai.app.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dorai.app.auth.LoginGateway
import dorai.app.auth.LoginViewModel
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
import dorai.app.roi.RoiViewModel
import dorai.shared.api.DoraiApi
import dorai.shared.api.MemoryTokenStore
import dorai.shared.model.Role
import dorai.shared.model.Tokens

object Sections {
    const val INVENTORY = "inventory"
    const val REVIEW = "review"
    const val FINDINGS = "findings"
    const val EXPORT = "export"
    const val ROI = "roi"
}

/** The workspace sections in Matija's frame: Luka's 01 and 02, Matija's 03 and 04, then the RoI (05). */
val shellSections: List<WorkspaceSection> = listOf(
    WorkspaceSection(Sections.INVENTORY, "Cloud inventory", "01"),
    WorkspaceSection(Sections.REVIEW, "AI review", "02"),
) + matijaSections + WorkspaceSection(Sections.ROI, "Register of Information", "05")

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
    val roi = RoiViewModel(sources.roi, canEdit = { session.role >= Role.Reviewer })

    /** The app only hides what the role can't use; the server checks every call. */
    val canReview: Boolean get() = session.role >= Role.Reviewer
}

/**
 * Root state of the app: server, connection, login, section, theme and language. Kept in memory for now.
 * The server starts as the development address and changes when an invite link is opened.
 * Tests replace [openApi] and the server calls ([probe], [gateway], [invites]); by default they go to the current server.
 */
class ShellViewModel(
    address: String = defaultServerAddress,
    private val openApi: (String) -> DoraiApi = { DoraiApi(it, tokens = MemoryTokenStore()) },
    probe: ServerProbe? = null,
    gateway: LoginGateway? = null,
    invites: InviteGateway? = null,
) : ViewModel() {
    var state by mutableStateOf(ShellState())
        private set

    /** The signed-in workspace, or null while the login page is shown. */
    var workspace by mutableStateOf<Workspace?>(null)
        private set

    /** Client for the current server; replaced when an invite link names another one. */
    private var api: DoraiApi = openApi(address)

    val connection = ConnectionViewModel(address, probe ?: ServerProbe { api.health() })
    val login = LoginViewModel(gateway ?: object : LoginGateway {
        override suspend fun login(username: String, password: String) = api.login(username, password)
        override suspend fun changePassword(username: String, currentPassword: String, newPassword: String) =
            api.changePassword(username, currentPassword, newPassword)
    })
    val invite = InviteViewModel(invites ?: InviteGateway { api.redeem(it) })

    /**
     * Opens an invite link (pasted on desktop, tapped on Android): switches to its server, redeems the
     * one-time code and leaves an empty login page. Problems show in the invite field.
     */
    suspend fun openInvite(text: String = invite.state.text) {
        if (invite.state.busy) return
        val link = invite.read(text) ?: return
        if (link.fingerprint != null) {
            // Certificate pinning: DoraiApi must trust only the certificate with this SHA-256 (shared lane, Ignjat).
            // Until it can, a secure link is refused instead of trusting any certificate.
            invite.fail(InviteViewModel.SECURE_NOT_READY, text)
            return
        }
        logout()
        switchServer(link.server)
        login.reset()
        invite.redeem(link.code)
        connection.check()
    }

    private fun switchServer(address: String) {
        if (address == connection.address) return
        api.close()
        api = openApi(address)
        connection.switchTo(address)
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
        workspace = Workspace(session, api)
        state = state.copy(section = Sections.INVENTORY)
    }

    /** Back to the login page at once, then ends the session on the server. */
    suspend fun logout() {
        if (workspace == null) return
        workspace = null
        api.logout()
    }

    fun select(section: String) {
        if (shellSections.any { it.id == section && it.enabled }) state = state.copy(section = section)
    }

    fun setTheme(theme: ThemeMode) { state = state.copy(theme = theme) }

    fun setLanguage(language: AppLanguage) { state = state.copy(language = language) }

    override fun onCleared() = api.close()
}
