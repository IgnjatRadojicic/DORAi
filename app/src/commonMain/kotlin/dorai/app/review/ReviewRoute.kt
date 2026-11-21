package dorai.app.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

@Composable
fun ReviewRoute(model: ReviewViewModel, modifier: Modifier = Modifier, canDecide: Boolean = true) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(model) { model.refresh() }
    ReviewScreen(
        state = model.state,
        onGenerate = { if (canDecide) scope.launch { model.generate() } },
        onRefresh = { scope.launch { model.refresh() } },
        onAccept = { id -> if (canDecide) scope.launch { model.accept(id) } },
        onReject = { id -> if (canDecide) scope.launch { model.reject(id) } },
        modifier = modifier,
        canDecide = canDecide,
    )
}
