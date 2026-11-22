package dorai.app.components

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun TableScrollbar(state: ScrollState, modifier: Modifier) {
    // Only when there is something to scroll.
    if (state.maxValue > 0) HorizontalScrollbar(rememberScrollbarAdapter(state), modifier)
}
