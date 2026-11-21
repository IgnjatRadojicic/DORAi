package dorai.app.inventory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/** Loads the stored inventory each time the section opens, so decisions made in review show up here. */
@Composable
fun InventoryRoute(model: InventoryViewModel, modifier: Modifier = Modifier, canScan: Boolean = true) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(model) { model.refresh() }
    InventoryScreen(
        state = model.state,
        onScan = { if (canScan) scope.launch { model.scan() } },
        onRefresh = { scope.launch { model.refresh() } },
        modifier = modifier,
        canScan = canScan,
    )
}
