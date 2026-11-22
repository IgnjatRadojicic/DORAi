package dorai.app.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** Escape anywhere inside runs [action], so a form can be left from the keyboard. */
fun Modifier.onEscape(enabled: Boolean = true, action: () -> Unit): Modifier = onPreviewKeyEvent {
    if (enabled && it.key == Key.Escape && it.type == KeyEventType.KeyDown) {
        action()
        true
    } else {
        false
    }
}

/** Puts the cursor in this field when it first shows, so typing can start at once. */
@Composable
fun Modifier.focusOnShow(enabled: Boolean = true): Modifier {
    val focus = remember { FocusRequester() }
    LaunchedEffect(enabled) { if (enabled) runCatching { focus.requestFocus() } }
    return focusRequester(focus)
}
