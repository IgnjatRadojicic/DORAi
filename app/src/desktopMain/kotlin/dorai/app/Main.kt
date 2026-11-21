package dorai.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dorai.app.components.DesktopTitleBar
import dorai.shared.Dorai

fun main() = application {
    // Maximized so desktop scaling never places controls off screen; Matija's title bar draws the window chrome.
    val windowState = rememberWindowState(width = 1280.dp, height = 820.dp, placement = WindowPlacement.Maximized)
    Window(onCloseRequest = ::exitApplication, title = Dorai.NAME, undecorated = true, state = windowState) {
        App(chrome = { DesktopTitleBar(windowState, ::exitApplication) })
    }
}
