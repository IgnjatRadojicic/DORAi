package dorai.app.export

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dorai.app.components.*
import dorai.app.theme.ProductStyle
import dorai.app.validation.*

@Composable
fun ValidationExportScreen(
    validation: ValidationState, export: ExportState,
    onRefresh: () -> Unit, onDownload: (ExportFormat) -> Unit,
    modifier: Modifier = Modifier, canExport: Boolean = true, isDemo: Boolean = false,
    availableFormats: Set<ExportFormat> = if (isDemo) emptySet() else ExportFormat.entries.toSet(),
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageHeading("Validation & export") {
            ProductButton(if (validation.busy) "Validating..." else "Refresh validation", onRefresh, outlined = true,
                enabled = !validation.busy, icon = ProductIcon.REFRESH)
        }
        ValidationMetrics(validation)
        ValidationReportPanel(validation, onRefresh, isDemo)
        validation.report?.errorCount?.takeIf { it > 0 }?.let { ExportErrorsBanner(it) }
        ExportDownloads(export, onDownload, canExport, isDemo, availableFormats)
    }
}

@Composable
fun ExportScreen(state: ExportState, onDownload: (ExportFormat) -> Unit, modifier: Modifier = Modifier, canExport: Boolean = true) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 64.dp)) {
        ExportDownloads(state, onDownload, canExport)
    }
}

@Composable
private fun ExportDownloads(state: ExportState, onDownload: (ExportFormat) -> Unit, canExport: Boolean, isDemo: Boolean = false,
    availableFormats: Set<ExportFormat> = if (isDemo) emptySet() else ExportFormat.entries.toSet()) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(uiText("Export"), style = MaterialTheme.typography.titleMedium)
            }
            Text(uiText("Register"), style = MaterialTheme.typography.bodySmall, color = ProductStyle.colors.muted)
        }
        BoxWithConstraints {
            val wide = maxWidth >= 760.dp
            val csv: @Composable (Modifier) -> Unit = { panelModifier ->
                ProductPanel(panelModifier) {
                    PanelHeading("", "Register CSV package") { OutlineChip("ZIP / DRAFT") }
                    Column(Modifier.padding(24.dp).heightIn(min = if (wide) 220.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        listOf("B_01.01" to "Financial entity", "B_02.01" to "Contractual arrangements", "B_02.02" to "ICT services",
                            "B_05.01" to "ICT third-party providers", "B_06.01" to "Business functions").forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlineChip(item.first)
                                Text(uiText(item.second), color = ProductStyle.colors.muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    ExportFooter(state, ExportFormat.CSV, onDownload, canExport, isDemo, availableFormats)
                }
            }
            val pdf: @Composable (Modifier) -> Unit = { panelModifier ->
                ProductPanel(panelModifier) {
                    PanelHeading("", "Register report") { OutlineChip("PDF / DRAFT") }
                    Column(Modifier.padding(24.dp).heightIn(min = if (wide) 220.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        listOf(
                            "Costs & concentration",
                            "Validation",
                            "Sources & evidence",
                        ).forEach { item ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                LineIcon(ProductIcon.FILE, size = 15.dp)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(uiText(item), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    ExportFooter(state, ExportFormat.PDF, onDownload, canExport, isDemo, availableFormats)
                }
            }
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) { csv(Modifier.weight(1f)); pdf(Modifier.weight(1f)) }
            else Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { csv(Modifier); pdf(Modifier) }
        }
        if (!canExport && !isDemo) Text(uiText("Read-only access"), color = ProductStyle.colors.muted)
        state.error?.let { ErrorView(it) }
        NoticeEffect(state.savedLocation?.let { uiFormat(UiCopy.FILE_SAVED, "path" to it) } ?: if (state.cancelled) uiText(UiCopy.SAVE_CANCELLED) else null)
    }
}

@Composable
private fun ExportFooter(state: ExportState, format: ExportFormat, onDownload: (ExportFormat) -> Unit, canExport: Boolean, isDemo: Boolean,
    availableFormats: Set<ExportFormat>) {
    HorizontalDivider(color = ProductStyle.colors.line)
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(12.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        MetaText(if (format !in availableFormats) "Unavailable" else if (isDemo) "Demo" else if (format == ExportFormat.CSV) "ZIP" else "PDF")
        ProductButton(if (state.busy == format) "Saving..." else if (format == ExportFormat.CSV) "Save CSV package" else "Save PDF report",
            { onDownload(format) }, outlined = format == ExportFormat.PDF, enabled = format in availableFormats && canExport && state.busy == null,
            icon = if (state.busy == format) ProductIcon.REFRESH else ProductIcon.DOWNLOAD)
    }
}

/** Stays above the export buttons while the register has errors; the dialog only asks once per sign-in. */
@Composable
private fun ExportErrorsBanner(errors: Int) {
    val colors = ProductStyle.colors
    Surface(shape = RoundedCornerShape(12.dp), color = colors.red.copy(alpha = 0.08f), border = BorderStroke(1.dp, colors.red.copy(alpha = 0.4f))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LineIcon(ProductIcon.ALERT, color = colors.red, size = 17.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(uiFormat(if (errors == 1) UiCopy.EXPORT_ERRORS_TITLE_ONE else UiCopy.EXPORT_ERRORS_TITLE_MANY, "count" to errors),
                    style = MaterialTheme.typography.titleSmall)
                Text(uiText(UiCopy.EXPORT_ERRORS_BODY), color = colors.muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
