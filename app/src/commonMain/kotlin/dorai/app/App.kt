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
import dorai.app.auth.LoginActions
import dorai.app.auth.LoginScreen
import dorai.app.auth.SessionStore
import dorai.app.auth.rememberSecretVault
import dorai.app.components.ControlCenterFrame
import dorai.app.connect.InviteField
import dorai.app.export.ExportViewModel
import dorai.app.export.ValidationExportRoute
import dorai.app.export.rememberWorkspaceFiles
import dorai.app.findings.FindingsRoute
import dorai.app.inventory.InventoryRoute
import dorai.app.review.ReviewRoute
import dorai.app.shell.AccountBar
import dorai.app.shell.LogoutButton
import dorai.app.shell.Sections
import dorai.app.shell.ShellViewModel
import dorai.app.shell.Workspace
import dorai.app.shell.shellSections
import dorai.app.theme.DoraiTheme
import kotlinx.coroutines.launch

/** An invite link the platform opened the app with. A new object each time, so the same link tapped twice is handled twice. */
class OpenedInvite(val link: String)

/**
 * Root of the app, shared by desktop and Android.
 * [chrome] is drawn above everything inside the theme, for example the desktop title bar.
 * [invite] is a `dorai://connect` link the platform opened the app with (Android deep link).
 */
@Composable
fun App(chrome: @Composable () -> Unit = {}, invite: OpenedInvite? = null) {
    val vault = rememberSecretVault()
    val shell = viewModel { ShellViewModel(sessions = SessionStore(vault)) }
    // The saved session first, so an invite link opened with the app can still replace it.
    LaunchedEffect(shell) { shell.restore() }
    LaunchedEffect(shell) { shell.connection.watch() }
    LaunchedEffect(invite) { invite?.let { shell.openInvite(it.link) } }
    val scope = rememberCoroutineScope()
    DoraiTheme(shell.state.theme, shell.state.language) {
        Column(Modifier.fillMaxSize()) {
            chrome()
            val workspace = shell.workspace
            if (workspace == null) {
                val login = shell.login
                LoginScreen(
                    state = login.state,
                    actions = remember(shell) {
                        LoginActions(
                            onUsernameChange = login::onUsernameChange,
                            onPasswordChange = login::onPasswordChange,
                            onLogin = { scope.launch { shell.login() } },
                            onNewPasswordChange = login::onNewPasswordChange,
                            onRepeatPasswordChange = login::onRepeatPasswordChange,
                            onChangePassword = { scope.launch { shell.changePassword() } },
                            onBack = login::backToSignIn,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    below = {
                        InviteField(
                            state = shell.invite.state,
                            onToggle = shell.invite::toggle,
                            onTextChange = shell.invite::onTextChange,
                            onUse = { scope.launch { shell.openInvite() } },
                        )
                    },
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
        account = { AccountBar(workspace.session) },
        footer = { LogoutButton(onLogout) },
    ) { section ->
        Box(Modifier.fillMaxSize()) {
            when (section) {
                Sections.INVENTORY -> InventoryRoute(workspace.inventory, canScan = workspace.canReview)
                Sections.REVIEW -> ReviewRoute(workspace.review, canDecide = workspace.canReview)
                Sections.FINDINGS -> FindingsRoute(workspace.findings, files, canCheck = workspace.canReview)
                else -> ValidationExportRoute(workspace.validation, export, canExport = workspace.canReview)
            }
        }
    }
}
