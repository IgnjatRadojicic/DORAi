package dorai.app.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dorai.app.components.ConfirmDialog
import dorai.app.components.UiCopy
import dorai.app.components.uiFormat
import dorai.app.components.uiText
import dorai.app.validation.ValidationViewModel
import kotlinx.coroutines.launch

@Composable
fun ValidationExportRoute(validation: ValidationViewModel, export: ExportViewModel,
    modifier: Modifier = Modifier, canExport: Boolean = true, isDemo: Boolean = false,
    availableFormats: Set<ExportFormat> = if (isDemo) emptySet() else ExportFormat.entries.toSet()) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(validation) { validation.refresh() }
    val errors = validation.state.report?.errorCount ?: 0
    var asking by remember { mutableStateOf<ExportFormat?>(null) }
    val save = { format: ExportFormat -> scope.launch { export.download(format) }; Unit }
    ValidationExportScreen(validation.state, export.state,
        onRefresh = { scope.launch { validation.refresh() } },
        onDownload = { format ->
            if (canExport && format in availableFormats) {
                if (errors > 0 && !export.errorsAcknowledged) asking = format else save(format)
            }
        },
        modifier = modifier, canExport = canExport, isDemo = isDemo, availableFormats = availableFormats)
    asking?.let { format ->
        ConfirmDialog(
            title = uiFormat(if (errors == 1) UiCopy.EXPORT_ERRORS_TITLE_ONE else UiCopy.EXPORT_ERRORS_TITLE_MANY, "count" to errors),
            body = uiText(UiCopy.EXPORT_ERRORS_BODY),
            confirm = uiText(UiCopy.EXPORT_ANYWAY),
            onConfirm = { asking = null; export.acknowledgeErrors(); save(format) },
            onDismiss = { asking = null },
            destructive = false,
        )
    }
}
