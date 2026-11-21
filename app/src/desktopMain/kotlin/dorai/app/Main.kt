package dorai.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dorai.shared.Dorai

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = Dorai.NAME) {
        App()
    }
}
