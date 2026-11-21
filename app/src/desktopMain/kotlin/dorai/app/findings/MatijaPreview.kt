package dorai.app.findings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dorai.app.components.ControlCenterFrame
import dorai.app.components.DesktopTitleBar
import dorai.app.components.WorkspaceSection
import dorai.app.components.matijaSections
import dorai.app.components.AppLanguage
import dorai.app.export.ExportViewModel
import dorai.app.export.ApiExportSource
import dorai.app.export.ExportFormat
import dorai.app.export.ValidationExportRoute
import dorai.app.export.rememberWorkspaceFiles
import dorai.app.theme.DoraiTheme
import dorai.app.theme.ThemeMode
import dorai.app.validation.ValidationViewModel
import dorai.app.validation.ApiRegisterValidation
import dorai.shared.api.DoraiApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Standalone preview using the same feature screens exposed to the production shell. */
fun main() = application {
    val windowState = rememberWindowState(width = 1280.dp, height = 820.dp, placement = WindowPlacement.Maximized)
    Window(onCloseRequest = ::exitApplication, title = "DORAi", undecorated = true, state = windowState) {
        var theme by remember { mutableStateOf(ThemeMode.DARK) }
        var page by remember { mutableStateOf("findings") }
        var choosingFile by remember { mutableStateOf(false) }
        var language by remember { mutableStateOf(AppLanguage.ENGLISH) }
        DoraiTheme(theme, language) {
            val files = rememberWorkspaceFiles()
            val scope = rememberCoroutineScope()
            val findings = remember { FindingsViewModel { plan -> withContext(Dispatchers.IO) { PreviewWorkspace.check(plan) } } }
            val api = remember { DoraiApi(System.getenv("DORAI_SERVER_URL")?.takeIf { it.isNotBlank() } ?: "http://localhost:8080") }
            DisposableEffect(api) { onDispose { api.close() } }
            val validation = remember(api) { ValidationViewModel(ApiRegisterValidation(api)) }
            val export = remember(api, files) { ExportViewModel(ApiExportSource(api), files) }

            LaunchedEffect(findings) {
                try {
                    findings.selectPlan(withContext(Dispatchers.IO) { PreviewWorkspace.plan() })
                    findings.check()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    findings.showFileError(error)
                }
            }

            val shortcuts = Modifier.onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) false
                else when (event.key) {
                    Key.Three -> { page = "findings"; true }
                    Key.Four -> { page = "export"; true }
                    Key.Enter -> {
                        if (page == "findings" && !choosingFile) scope.launch { findings.check() }
                        true
                    }
                    Key.O -> {
                        if (!choosingFile && !findings.state.busy) scope.launch {
                            choosingFile = true
                            page = "findings"
                            try { findings.selectPlan(files.openPlan()) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (error: Exception) { findings.showFileError(error) }
                            finally { choosingFile = false }
                        }
                        true
                    }
                    else -> false
                }
            }

            Column(Modifier.fillMaxSize().then(shortcuts)) {
                DesktopTitleBar(windowState, ::exitApplication)
                ControlCenterFrame(
                    selected = page, onSection = { page = it }, theme = theme, onTheme = { theme = it }, isDemo = true,
                    modifier = Modifier.weight(1f),
                    onLanguage = { language = it },
                    sections = listOf(
                        WorkspaceSection("inventory", "Cloud inventory", "01", enabled = false),
                        WorkspaceSection("review", "AI review", "02", enabled = false),
                    ) + matijaSections,
                ) { activeSection ->
                    when (activeSection) {
                        "findings" -> FindingsRoute(findings, files, isDemo = true, onLoadDemo = {
                            scope.launch {
                                try { findings.selectPlan(withContext(Dispatchers.IO) { PreviewWorkspace.plan() }) }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (error: Exception) { findings.showFileError(error) }
                            }
                        })
                        else -> ValidationExportRoute(validation, export, isDemo = true, availableFormats = ExportFormat.entries.toSet())
                    }
                }
            }
        }
    }
}
