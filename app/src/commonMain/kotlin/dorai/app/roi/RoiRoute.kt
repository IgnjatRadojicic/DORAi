package dorai.app.roi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

@Composable
fun RoiRoute(model: RoiViewModel, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(model) { model.refresh() }
    RoiScreen(
        model.state,
        RoiActions(
            refresh = { scope.launch { model.refresh() } },
            select = model::select,
            edit = model::startEdit,
            editable = { row, column -> model.editable(model.state.template, row, column) },
            type = model::type,
            save = { scope.launch { model.save() } },
            cancel = model::cancel,
        ),
        modifier,
    )
}
