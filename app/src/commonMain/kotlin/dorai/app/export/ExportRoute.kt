package dorai.app.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

@Composable
fun ExportRoute(model: ExportViewModel, modifier: Modifier = Modifier, canExport: Boolean = true) {
    val scope = rememberCoroutineScope()
    ExportScreen(model.state, onDownload = { format ->
        if (canExport) scope.launch { model.download(format) }
    }, modifier = modifier, canExport = canExport)
}
