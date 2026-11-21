package dorai.app.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dorai.app.validation.ValidationViewModel
import kotlinx.coroutines.launch

@Composable
fun ValidationExportRoute(validation: ValidationViewModel, export: ExportViewModel,
    modifier: Modifier = Modifier, canExport: Boolean = true, isDemo: Boolean = false,
    availableFormats: Set<ExportFormat> = if (isDemo) emptySet() else ExportFormat.entries.toSet()) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(validation) { validation.refresh() }
    ValidationExportScreen(validation.state, export.state,
        onRefresh = { scope.launch { validation.refresh() } },
        onDownload = { format -> if (canExport && format in availableFormats) scope.launch { export.download(format) } },
        modifier = modifier, canExport = canExport, isDemo = isDemo, availableFormats = availableFormats)
}
