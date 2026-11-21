package dorai.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dorai.app.auth.LoginScreen
import dorai.app.components.ControlCenterFrame
import dorai.app.components.ProductIcon
import dorai.app.connect.ConnectionMark
import dorai.app.export.ExportViewModel
import dorai.app.export.ValidationExportRoute
import dorai.app.export.rememberWorkspaceFiles
import dorai.app.findings.FindingsRoute
import dorai.app.shell.AccountBar
import dorai.app.shell.SectionPlaceholder
import dorai.app.shell.Sections
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
    LaunchedEffect(shell) { shell.connection.watch() }
    val scope = rememberCoroutineScope()
    DoraiTheme(shell.state.theme, shell.state.language) {
        Column(Modifier.fillMaxSize()) {
            chrome()
            val workspace = shell.workspace
            if (workspace == null) {
                LoginScreen(
                    state = shell.login.state,
                    onUsernameChange = shell.login::onUsernameChange,
                    onPasswordChange = shell.login::onPasswordChange,
                    onLogin = { scope.launch { shell.login() } },
                    modifier = Modifier.weight(1f),
                    corner = { ConnectionMark(shell.connection.status, shell.connection.address) },
                )
            } else {
                WorkspaceShell(shell, workspace, onLogout = { scope.launch { shell.logout() } }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WorkspaceShell(shell: ShellViewModel, workspace: Workspace, onLogout: () -> Unit, modifier: Modifier) {
    val files = rememberWorkspaceFiles()
    // File access is tied to the window or activity, so the export model lives with the composition.
    val export = remember(workspace, files) { ExportViewModel(workspace.sources.export, files, canExport = { workspace.canReview }) }
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
            AccountBar(workspace.session, onLogout)
            Box(Modifier.weight(1f)) {
                when (section) {
                    Sections.INVENTORY -> SectionPlaceholder(
                        "Cloud inventory", "Scanned resources will be listed here.", ProductIcon.CLOUD)
                    Sections.REVIEW -> SectionPlaceholder(
                        "AI review", "AI suggestions with their citations will be listed here.", ProductIcon.BOOK)
                    Sections.FINDINGS -> FindingsRoute(workspace.findings, files, canCheck = workspace.canReview)
                    else -> ValidationExportRoute(workspace.validation, export, canExport = workspace.canReview)
                }
            }
        }
    }
}
