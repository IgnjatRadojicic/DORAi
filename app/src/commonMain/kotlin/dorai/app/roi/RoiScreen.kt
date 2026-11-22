package dorai.app.roi

import dorai.app.components.uiText
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import dorai.app.theme.ProductShapes
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.window.Dialog
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
import dorai.app.components.NoticeEffect
import dorai.app.components.SelectChip
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
import dorai.app.components.UiCopy
import dorai.app.components.uiFormat

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
                Metric("Errors", view.errorCount, "Block the final export", ProductIcon.SHIELD, error = true),
                Metric("Warnings", view.warningCount, "To review", ProductIcon.ALERT, view.warningCount > 0),
                Metric("Templates", view.tables.size, uiFormat(UiCopy.REFERENCE_DATE, "date" to view.referenceDate), ProductIcon.ARCHIVE),
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
                        SelectChip(label, t.code == state.template, { actions.select(t.code) })
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
            OutlineChip(if (errors == 0) "Ready" else uiFormat(UiCopy.TO_FIX, "count" to errors), if (errors == 0) ProductStyle.colors.accent else ProductStyle.colors.amber, dot = true)
        }
        state.edit?.takeIf { it.template == template.code }?.let { edit -> CellEditor(edit, state.saving, actions) }
        NoticeEffect(state.message)
        if (rows.isEmpty()) {
            EmptyView("No rows", "Nothing in the register feeds this template yet.")
        } else {
            val columns = template.columns
            // Column codes always with their names: "0090 Termination reason".
            ProductTable(columns.map { "${it.header.removePrefix("c")} ${it.name}" }, columns.map { 1f }, minWidth = maxOf(850, columns.size * 170)) {
                rows.forEach { row ->
                    ProductTableRow {
                        columns.forEach { col ->
                            val canEdit = actions.editable(row.key, col.code)
                            Cell(row.cells[col.code], col.code, canEdit, Modifier.weight(1f).let { if (canEdit) it.clickable { actions.edit(row.key, col.code) } else it })
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
private fun Cell(cell: RoiCell?, column: String, editable: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val value = cell?.value
        // A chosen code shows with its name ("S18 Cloud services: PaaS").
        val named = value?.let { v -> RoiSpec.column(column)?.label(v)?.let { "$v $it" } ?: v }
        Text(named ?: if (editable) uiText("Add") else "", style = MaterialTheme.typography.bodyMedium,
            color = if (value == null) (if (editable) ProductStyle.colors.accent else ProductStyle.colors.muted) else ProductStyle.colors.ink)
        cell?.let {
            val label = uiText(it.label?.let(::labelText) ?: "derived")
            MetaText(it.confirmedBy?.let { by -> uiFormat(UiCopy.CONFIRMED_BY, "label" to label, "name" to by) } ?: label)
        }
    }
}

private fun labelText(label: ProvenanceLabel) = when (label) {
    ProvenanceLabel.Scanned -> "scanned"
    ProvenanceLabel.Planned -> "planned"
    ProvenanceLabel.AiSuggested -> "ai-suggested"
    ProvenanceLabel.HumanConfirmed -> "human-confirmed"
}

/**
 * Edits one cell: a bottom sheet on phones, a dialog on wider screens. Back, Escape or a tap outside cancel;
 * Enter saves. The table underneath stays where it was.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CellEditor(edit: CellEdit, saving: Boolean, actions: RoiActions) {
    BoxWithConstraints {
        if (maxWidth < 600.dp) {
            ModalBottomSheet(onDismissRequest = actions.cancel, containerColor = ProductStyle.colors.panel, shape = ProductShapes.dialog) {
                EditorContent(edit, saving, actions, Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp).imePadding())
            }
        } else {
            Dialog(onDismissRequest = actions.cancel) {
                Surface(Modifier.widthIn(max = 560.dp), shape = ProductShapes.dialog, color = ProductStyle.colors.panel) {
                    EditorContent(edit, saving, actions, Modifier.padding(24.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorContent(edit: CellEdit, saving: Boolean, actions: RoiActions, modifier: Modifier) {
    val column = RoiSpec.column(edit.column)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${edit.column.substringAfterLast('.')} ${column?.name.orEmpty()}", style = MaterialTheme.typography.titleLarge)
        MetaText("${edit.column.substringBeforeLast('.')}  ${uiText("Row")} ${edit.rowKey}")
        OutlinedTextField(edit.text, actions.type, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true, enabled = !saving,
            label = { Text(uiText(if (column?.options.isNullOrEmpty()) "Value" else "Code or search")) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { actions.save() }))
        // Long lists (131 licensed activities) are searched: typing filters by code or words of the label.
        column?.options?.takeIf { it.isNotEmpty() }?.let { options ->
            val picked = options.firstOrNull { it.key == edit.text.trim() }
            if (picked != null) {
                Text("${picked.key} ${picked.label}", color = ProductStyle.colors.accent, style = MaterialTheme.typography.bodyMedium)
            } else {
                val matches = matching(options, edit.text)
                MetaText(if (edit.text.isBlank()) uiFormat(UiCopy.PICK_OPTION, "count" to options.size)
                    else uiFormat(UiCopy.OPTIONS_MATCH, "count" to matches.size, "total" to options.size))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    matches.take(12).forEach { option -> SelectChip("${option.key} ${option.label}", false, { actions.type(option.key) }, enabled = !saving) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            ProductButton("Cancel", actions.cancel, outlined = true, enabled = !saving)
            ProductButton(if (saving) "Saving..." else "Save", actions.save, enabled = !saving)
        }
    }
}
