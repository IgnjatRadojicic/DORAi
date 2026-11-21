package dorai.app.roi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dorai.app.components.EmptyView
import dorai.app.components.ErrorView
import dorai.app.components.LoadingView
import dorai.app.components.MetaText
import dorai.app.components.Metric
import dorai.app.components.MetricGrid
import dorai.app.components.OutlineChip
import dorai.app.components.PageHeading
import dorai.app.components.PanelHeading
import dorai.app.components.ProductButton
import dorai.app.components.ProductIcon
import dorai.app.components.ProductPanel
import dorai.app.components.ProductTable
import dorai.app.components.ProductTableRow
import dorai.app.theme.ProductStyle
import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.ValidationSeverity
import dorai.shared.roi.RoiCell
import dorai.shared.roi.RoiSpec
import dorai.app.register.matching

/** Callbacks of the RoI screen; the screen never talks to the server itself. */
class RoiActions(
    val refresh: () -> Unit,
    val select: (String) -> Unit,
    val edit: (rowKey: String, column: String) -> Unit,
    val editable: (rowKey: String, column: String) -> Boolean,
    val type: (String) -> Unit,
    val save: () -> Unit,
    val cancel: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoiScreen(state: RoiState, actions: RoiActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 64.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        PageHeading("Register of Information") {
            ProductButton(if (state.busy) "Loading..." else "Refresh", actions.refresh, outlined = true, enabled = !state.busy, icon = ProductIcon.REFRESH)
        }
        state.view?.let { view ->
            MetricGrid(listOf(
                Metric("Errors", view.errorCount, "Block the final export", ProductIcon.SHIELD, view.errorCount > 0),
                Metric("Warnings", view.warningCount, "To review", ProductIcon.ALERT, view.warningCount > 0),
                Metric("Templates", view.tables.size, "Reference date ${view.referenceDate}", ProductIcon.ARCHIVE),
            ))
        }
        when {
            state.busy && state.view == null -> LoadingView("Building the register...")
            state.error != null -> ErrorView(state.error, actions.refresh)
            state.view == null -> EmptyView("No register loaded", "")
            else -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.templates.forEach { t ->
                        val label = t.code + if (t.errors > 0) " (${t.errors})" else ""
                        ProductButton(label, { actions.select(t.code) }, outlined = t.code != state.template)
                    }
                }
                TemplatePanel(state, actions)
            }
        }
    }
}

@Composable
private fun TemplatePanel(state: RoiState, actions: RoiActions) {
    val template = RoiSpec.template(state.template)
    val rows = state.view?.tables?.get(template.code).orEmpty()
    val issues = state.view?.issues.orEmpty().filter { it.template == template.code }
    ProductPanel {
        PanelHeading(template.code, template.name) {
            val errors = issues.count { it.severity == ValidationSeverity.Error }
            OutlineChip(if (errors == 0) "Ready" else "$errors to fix", if (errors == 0) ProductStyle.colors.accent else ProductStyle.colors.amber, dot = true)
        }
        state.edit?.takeIf { it.template == template.code }?.let { edit -> EditBar(edit, state.saving, actions) }
        state.message?.let { Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) { MetaText(it) } }
        if (rows.isEmpty()) {
            EmptyView("No rows", "Nothing in the register feeds this template yet.")
        } else {
            val columns = template.columns
            ProductTable(columns.map { it.header.removePrefix("c") }, columns.map { 1f }, minWidth = maxOf(850, columns.size * 130)) {
                rows.forEach { row ->
                    ProductTableRow {
                        columns.forEach { col ->
                            val canEdit = actions.editable(row.key, col.code)
                            Cell(row.cells[col.code], canEdit, Modifier.weight(1f).let { if (canEdit) it.clickable { actions.edit(row.key, col.code) } else it })
                        }
                    }
                }
            }
        }
        if (issues.isNotEmpty()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                issues.sortedBy { it.severity.ordinal }.forEach { issue ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        OutlineChip(if (issue.severity == ValidationSeverity.Error) "Error" else "Warning",
                            if (issue.severity == ValidationSeverity.Error) ProductStyle.colors.red else ProductStyle.colors.amber, dot = true)
                        Column(Modifier.weight(1f)) {
                            Text(issue.message, style = MaterialTheme.typography.bodyMedium)
                            MetaText("${issue.field}  ${issue.entityId.orEmpty()}  ${issue.code}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(cell: RoiCell?, editable: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(cell?.value ?: if (editable) "Add" else "", fontFamily = ProductStyle.fonts.mono, fontSize = 12.sp,
            color = if (cell?.value == null) ProductStyle.colors.muted else ProductStyle.colors.ink)
        cell?.let { MetaText((it.label?.let(::labelText) ?: "derived") + (it.confirmedBy?.let { by -> " by $by" } ?: "")) }
    }
}

private fun labelText(label: ProvenanceLabel) = when (label) {
    ProvenanceLabel.Scanned -> "scanned"
    ProvenanceLabel.Planned -> "planned"
    ProvenanceLabel.AiSuggested -> "ai-suggested"
    ProvenanceLabel.HumanConfirmed -> "human-confirmed"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditBar(edit: CellEdit, saving: Boolean, actions: RoiActions) {
    val column = RoiSpec.column(edit.column)
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MetaText("${edit.column}: ${column?.name.orEmpty()}  (row ${edit.rowKey})")
        // Long lists (131 licensed activities) are searched: typing filters by code or words of the label.
        column?.options?.takeIf { it.isNotEmpty() }?.let { options ->
            val picked = options.firstOrNull { it.key == edit.text.trim() }
            if (picked != null) {
                MetaText("${picked.key} = ${picked.label}")
            } else {
                val matches = matching(options, edit.text)
                MetaText(if (edit.text.isBlank()) "Type to search ${options.size} options, then pick one." else "${matches.size} of ${options.size} options match.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    matches.take(12).forEach { option -> ProductButton("${option.key}: ${option.label}", { actions.type(option.key) }, outlined = true, enabled = !saving) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(edit.text, actions.type, Modifier.weight(1f), singleLine = true, enabled = !saving)
            ProductButton(if (saving) "Saving..." else "Save", actions.save, enabled = !saving)
            ProductButton("Cancel", actions.cancel, outlined = true, enabled = !saving)
        }
    }
}
