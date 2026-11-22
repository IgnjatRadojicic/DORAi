package dorai.app

import dorai.app.roi.RoiRoute
import dorai.app.admin.AdminRoute
import dorai.app.register.RegisterRoute
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dorai.app.auth.LoginActions
import dorai.app.auth.LoginScreen
import dorai.app.components.ControlCenterFrame
import dorai.app.connect.InviteField
import dorai.app.export.ExportViewModel
import dorai.app.export.ValidationExportRoute
import dorai.app.export.rememberWorkspaceFiles
import dorai.app.findings.FindingsRoute
import dorai.app.inventory.InventoryRoute
import dorai.app.review.ReviewRoute
import dorai.app.shell.AccountBar
import dorai.app.shell.AppStorage
import dorai.app.shell.LogoutButton
import dorai.app.shell.Sections
import dorai.app.shell.ShellViewModel
import dorai.app.shell.Workspace
import dorai.app.theme.DoraiTheme
import dorai.app.theme.ProductStyle
import dorai.app.theme.ThemeMode
import kotlinx.coroutines.launch

/** An invite link the platform opened the app with. A new object each time, so the same link tapped twice is handled twice. */
class OpenedInvite(val link: String)

/**
 * Root of the app, shared by desktop and Android.
 * [chrome] is drawn above everything inside the theme, for example the desktop title bar.
 * [invite] is a `dorai://connect` link the platform opened the app with (Android deep link).
 * [storage] keeps the server, settings and session between starts; the platform builds it.
 * [onDarkTheme] tells the platform which theme is showing (Android colours the status bar icons with it).
 * [backHandler] is the platform's Back button (Android); the app passes whether it wants Back and what Back does.
 */
@Composable
fun App(
    chrome: @Composable () -> Unit = {},
    invite: OpenedInvite? = null,
    storage: AppStorage = AppStorage.inMemory(),
    onDarkTheme: (Boolean) -> Unit = {},
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
) {
    val shell = viewModel { ShellViewModel(storage = storage) }
    LaunchedEffect(shell) { shell.restore() }
    LaunchedEffect(shell) { shell.connection.watch() }
    // After restore, so the saved server never replaces the one in the link.
    LaunchedEffect(invite, shell.ready) { if (shell.ready) invite?.let { shell.openInvite(it.link) } }
    val scope = rememberCoroutineScope()
    val dark = shell.state.theme == ThemeMode.DARK || (shell.state.theme == ThemeMode.SYSTEM && isSystemInDarkTheme())
    SideEffect { onDarkTheme(dark) }
    // Back returns to the previous section; with none left the platform handles it (Android closes the app).
    backHandler(shell.workspace != null && shell.canGoBack, shell::back)
    DoraiTheme(shell.state.theme, shell.state.language) {
        // The background runs under the system bars (Android edge to edge); the content stays clear of them and of the keyboard.
        Column(Modifier.fillMaxSize().background(ProductStyle.colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
            chrome()
            val workspace = shell.workspace
            if (!shell.ready) {
                // A moment while the saved session is read; neither page flashes before it.
                Box(Modifier.weight(1f).fillMaxSize())
            } else if (workspace == null) {
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
        sections = workspace.sections,
        onLanguage = shell::setLanguage,
        account = { AccountBar(workspace.session) },
        footer = { LogoutButton(onLogout) },
    ) { section ->
        Box(Modifier.fillMaxSize()) {
            when (section) {
                Sections.INVENTORY -> InventoryRoute(workspace.inventory, canScan = workspace.canReview)
                Sections.REVIEW -> ReviewRoute(workspace.review, canDecide = workspace.canReview)
                Sections.FINDINGS -> FindingsRoute(workspace.findings, files, canCheck = workspace.canReview)
                Sections.ROI -> RoiRoute(workspace.roi)
                Sections.REGISTER -> RegisterRoute(workspace.register)
                Sections.ADMIN -> AdminRoute(workspace.admin)
                else -> ValidationExportRoute(workspace.validation, export, canExport = workspace.canReview)
            }
        }
    }
}
