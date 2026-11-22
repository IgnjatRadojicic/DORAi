package dorai.app.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dorai.app.theme.ProductShapes
import dorai.app.theme.ProductStyle

/**
 * One place for results (saved, deleted, invite created, scan finished): a short message at the bottom of the
 * window, so a result never lands off screen. The workspace frame owns the host; screens only call [NoticeEffect].
 */
val LocalNotices = staticCompositionLocalOf<SnackbarHostState?> { null }

/**
 * Shows [message] once for each new [key] (by default the text itself; pass an event id when the same text can
 * come twice in a row). Nothing when it is null or outside the workspace. What was shown is kept with the
 * section's saved state, so coming back to a section doesn't repeat it.
 */
@Composable
fun NoticeEffect(message: String?, key: Any? = message) {
    val host = LocalNotices.current ?: return
    val text = message?.let { uiText(it) }
    val event = key?.toString()
    var shown by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(text, event) {
        if (text == null) shown = null
        else if (event != shown) {
            shown = event
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(text, withDismissAction = true, duration = SnackbarDuration.Short)
        }
    }
}

@Composable
fun NoticeHost(host: SnackbarHostState, modifier: Modifier = Modifier) {
    val colors = ProductStyle.colors
    SnackbarHost(host, modifier.padding(16.dp).widthIn(max = 560.dp)) { data ->
        Snackbar(data, shape = ProductShapes.control, containerColor = colors.ink, contentColor = colors.background,
            dismissActionContentColor = colors.background, actionColor = colors.accent)
    }
}
