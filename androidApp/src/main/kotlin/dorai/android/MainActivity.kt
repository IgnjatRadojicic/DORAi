package dorai.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dorai.app.App
import dorai.app.OpenedInvite
import dorai.app.shell.androidStorage

class MainActivity : ComponentActivity() {
    /** The invite link from the admin's email, when the app was opened by tapping it. */
    private var invite by mutableStateOf<OpenedInvite?>(null)

    /** The theme the system bars were last coloured for; null before the first frame. */
    private var barsDark: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app draws under the status and navigation bars; App keeps its content clear of them.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // After a rotation the activity is recreated with the same intent; the link was already used then.
        if (savedInstanceState == null) invite = intent.inviteLink()
        val storage = androidStorage(this)
        setContent {
            App(
                invite = invite,
                storage = storage,
                onDarkTheme = ::colourSystemBars,
                backHandler = { enabled, onBack -> BackHandler(enabled, onBack) },
            )
        }
    }

    /** Light icons on the dark theme, dark icons on the light theme, following the app's theme rather than the system's. */
    private fun colourSystemBars(dark: Boolean) {
        if (barsDark == dark) return
        barsDark = dark
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, OLD_ANDROID_SCRIM)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    /** The app was already open (singleTop) and another invite link was tapped. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.inviteLink()?.let { invite = it }
    }

    private companion object {
        /** Behind the bars on Android versions that can't draw dark bar icons, so the white ones stay readable. */
        val OLD_ANDROID_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }

    private fun Intent.inviteLink(): OpenedInvite? =
        data?.takeIf { action == Intent.ACTION_VIEW && it.scheme == "dorai" && it.host == "connect" }
            ?.let { OpenedInvite(it.toString()) }
}
