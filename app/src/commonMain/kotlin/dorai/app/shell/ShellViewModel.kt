package dorai.app.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dorai.app.components.AppLanguage
import dorai.app.components.WorkspaceSection
import dorai.app.components.matijaSections
import dorai.app.connect.ConnectViewModel
import dorai.app.connect.ServerProbe
import dorai.app.connect.defaultServerAddress
import dorai.app.findings.FindingsViewModel
import dorai.app.theme.ThemeMode
import dorai.app.validation.ValidationViewModel
import dorai.shared.api.DoraiApi

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

/** Everything that belongs to one server connection. Closed when the user changes server. */
class Workspace(val address: String, val api: DoraiApi) : AutoCloseable {
    val sources = ServerSources(api)
    val findings = FindingsViewModel(sources.findings)
    val validation = ValidationViewModel(sources.validation)
    override fun close() = api.close()
}

/** Root state of the app: which server, which section, theme and language. Kept in memory for now. */
class ShellViewModel(
    probe: ServerProbe = ServerProbe { address -> DoraiApi(address).use { it.health() } },
    private val openApi: (String) -> DoraiApi = { DoraiApi(it) },
) : ViewModel() {
    var state by mutableStateOf(ShellState())
        private set

    /** The connected workspace, or null while the Connect screen is shown. */
    var workspace by mutableStateOf<Workspace?>(null)
        private set

    val connect = ConnectViewModel(defaultServerAddress, probe)

    /** Runs the Connect screen's check and opens a fresh workspace when the server answers. */
    suspend fun connect() {
        val address = connect.connect() ?: return
        workspace?.close()
        workspace = Workspace(address, openApi(address))
        state = state.copy(section = Sections.INVENTORY)
    }

    /** Back to the Connect screen; results from the old server are dropped with its workspace. */
    fun changeServer() {
        workspace?.close()
        workspace = null
    }

    fun select(section: String) {
        if (shellSections.any { it.id == section && it.enabled }) state = state.copy(section = section)
    }

    fun setTheme(theme: ThemeMode) { state = state.copy(theme = theme) }

    fun setLanguage(language: AppLanguage) { state = state.copy(language = language) }

    override fun onCleared() = changeServer()
}
