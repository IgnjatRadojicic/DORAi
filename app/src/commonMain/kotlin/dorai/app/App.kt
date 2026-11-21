package dorai.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dorai.app.components.ControlCenterFrame
import dorai.app.components.ProductIcon
import dorai.app.connect.ConnectScreen
import dorai.app.export.ExportViewModel
import dorai.app.export.ValidationExportRoute
import dorai.app.export.rememberWorkspaceFiles
import dorai.app.findings.FindingsRoute
import dorai.app.shell.SectionPlaceholder
import dorai.app.shell.Sections
import dorai.app.shell.ServerBar
import dorai.app.shell.ShellViewModel
import dorai.app.shell.Workspace
import dorai.app.shell.shellSections
import dorai.app.theme.DoraiTheme
import kotlinx.coroutines.launch

/**
 * Root of the app, shared by desktop and Android.
 * [chrome] is drawn above everything inside the theme, for example the desktop title bar.
 */
@Composable
fun App(chrome: @Composable () -> Unit = {}) {
    val shell = viewModel { ShellViewModel() }
    DoraiTheme(shell.state.theme, shell.state.language) {
        Column(Modifier.fillMaxSize()) {
            chrome()
            val workspace = shell.workspace
            if (workspace == null) {
                val scope = rememberCoroutineScope()
                ConnectScreen(
                    state = shell.connect.state,
                    onAddressChange = shell.connect::onAddressChange,
                    onConnect = { scope.launch { shell.connect() } },
                    modifier = Modifier.weight(1f),
                )
            } else {
                WorkspaceShell(shell, workspace, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WorkspaceShell(shell: ShellViewModel, workspace: Workspace, modifier: Modifier) {
    val files = rememberWorkspaceFiles()
    // File access is tied to the window or activity, so the export model lives with the composition.
    val export = remember(workspace, files) { ExportViewModel(workspace.sources.export, files) }
    ControlCenterFrame(
        selected = shell.state.section,
        onSection = shell::select,
        theme = shell.state.theme,
        onTheme = shell::setTheme,
        modifier = modifier,
        sections = shellSections,
        onLanguage = shell::setLanguage,
    ) { section ->
        Column(Modifier.fillMaxSize()) {
            ServerBar(workspace.address, shell::changeServer)
            Box(Modifier.weight(1f)) {
                when (section) {
                    Sections.INVENTORY -> SectionPlaceholder(
                        "Cloud inventory", "Scanned resources will be listed here.", ProductIcon.CLOUD)
                    Sections.REVIEW -> SectionPlaceholder(
                        "AI review", "AI suggestions with their citations will be listed here.", ProductIcon.BOOK)
                    Sections.FINDINGS -> FindingsRoute(workspace.findings, files)
                    else -> ValidationExportRoute(workspace.validation, export)
                }
            }
        }
    }
}
