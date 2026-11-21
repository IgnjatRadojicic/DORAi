package dorai.app.export

import androidx.compose.foundation.layout.*
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
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageHeading("Validation & export") {
            ProductButton(if (validation.busy) "Validating..." else "Refresh validation", onRefresh, outlined = true,
                enabled = !validation.busy, icon = ProductIcon.REFRESH)
        }
        ValidationMetrics(validation)
        ValidationReportPanel(validation, onRefresh, isDemo)
        ExportDownloads(export, onDownload, canExport, isDemo)
    }
}

@Composable
fun ExportScreen(state: ExportState, onDownload: (ExportFormat) -> Unit, modifier: Modifier = Modifier, canExport: Boolean = true) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 64.dp)) {
        ExportDownloads(state, onDownload, canExport)
    }
}

@Composable
private fun ExportDownloads(state: ExportState, onDownload: (ExportFormat) -> Unit, canExport: Boolean, isDemo: Boolean = false) {
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
                    ExportFooter(state, ExportFormat.CSV, onDownload, canExport, isDemo)
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
                    ExportFooter(state, ExportFormat.PDF, onDownload, canExport, isDemo)
                }
            }
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) { csv(Modifier.weight(1f)); pdf(Modifier.weight(1f)) }
            else Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { csv(Modifier); pdf(Modifier) }
        }
        if (!canExport && !isDemo) Text(uiText("Read-only access"), color = ProductStyle.colors.muted)
        state.error?.let { ErrorView(it) }
        state.savedLocation?.let { location ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LineIcon(ProductIcon.CHECK, color = ProductStyle.colors.green)
                Text(uiText("File saved") + " / " + location, color = ProductStyle.colors.green)
            }
        }
        if (state.cancelled) Text(uiText("Save cancelled. No new file was saved."), color = ProductStyle.colors.muted)
    }
}

@Composable
private fun ExportFooter(state: ExportState, format: ExportFormat, onDownload: (ExportFormat) -> Unit, canExport: Boolean, isDemo: Boolean) {
    HorizontalDivider(color = ProductStyle.colors.line)
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(12.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        MetaText(if (isDemo) "Demo" else if (format == ExportFormat.CSV) "ZIP" else "PDF")
        ProductButton(if (state.busy == format) "Saving..." else if (format == ExportFormat.CSV) "Save CSV package" else "Save PDF report",
            { onDownload(format) }, outlined = format == ExportFormat.PDF, enabled = !isDemo && canExport && state.busy == null,
            icon = if (state.busy == format) ProductIcon.REFRESH else ProductIcon.DOWNLOAD)
    }
}
