package dorai.app.validation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

@Composable
fun ValidationRoute(model: ValidationViewModel, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(model) { model.refresh() }
    ValidationScreen(model.state, onRefresh = { scope.launch { model.refresh() } }, modifier = modifier)
}
