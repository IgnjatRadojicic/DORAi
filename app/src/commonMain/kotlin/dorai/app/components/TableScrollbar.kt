package dorai.app.components

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A visible sideways scrollbar under a wide table. On desktop a mouse wheel scrolls only up and down, so
 * without it the columns past the window edge look cut off. Android scrolls with a swipe and shows none.
 */
@Composable
expect fun TableScrollbar(state: ScrollState, modifier: Modifier = Modifier)
