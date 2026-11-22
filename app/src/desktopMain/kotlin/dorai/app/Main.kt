package dorai.app

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dorai.app.components.DesktopTitleBar
import dorai.app.shell.desktopStorage
import dorai.app.resources.Res
import dorai.app.resources.dorai_app_icon
import dorai.shared.Dorai
import org.jetbrains.compose.resources.painterResource

fun main() = application {
    val storage = remember { desktopStorage() }
    // Maximized so desktop scaling never places controls off screen; Matija's title bar draws the window chrome.
    val windowState = rememberWindowState(width = 1280.dp, height = 820.dp, placement = WindowPlacement.Maximized)
    // The icon shows in the taskbar and Alt Tab; installers take theirs from app/icons.
    Window(onCloseRequest = ::exitApplication, title = Dorai.NAME, icon = painterResource(Res.drawable.dorai_app_icon),
        undecorated = true, state = windowState) {
        App(chrome = { DesktopTitleBar(windowState, ::exitApplication) }, storage = storage)
    }
}
