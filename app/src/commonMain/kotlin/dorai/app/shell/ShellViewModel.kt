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
import dorai.app.connect.ServerProbe
import dorai.app.connect.defaultServerAddress
import dorai.app.findings.FindingsViewModel
import dorai.app.inventory.InventoryViewModel
import dorai.app.review.ReviewViewModel
import dorai.app.theme.ThemeMode
import dorai.app.validation.ValidationViewModel
import dorai.shared.api.DoraiApi
import dorai.shared.api.MemoryTokenStore
import dorai.shared.model.Role
import dorai.shared.model.Tokens

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

/** Root state of the app: connection, login, section, theme and language. Kept in memory for now. */
class ShellViewModel(
    address: String = defaultServerAddress,
    private val api: DoraiApi = DoraiApi(address, tokens = MemoryTokenStore()),
    probe: ServerProbe = ServerProbe { api.health() },
    gateway: LoginGateway = LoginGateway { username, password -> api.login(username, password) },
) : ViewModel() {
    var state by mutableStateOf(ShellState())
        private set

    /** The signed-in workspace, or null while the login page is shown. */
    var workspace by mutableStateOf<Workspace?>(null)
        private set

    val connection = ConnectionViewModel(address, probe)
    val login = LoginViewModel(gateway)

    /** Runs the login page and opens the platform when the server accepts it. */
    suspend fun login() {
        val session = login.login() ?: return
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
