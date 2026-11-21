package dorai.app.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dorai.app.components.EmptyView
import dorai.app.components.ErrorView
import dorai.app.components.LoadingView
import dorai.app.components.MetaText
import dorai.app.components.PageHeading
import dorai.app.components.PanelHeading
import dorai.app.components.ProductButton
import dorai.app.components.ProductIcon
import dorai.app.components.ProductPanel
import dorai.app.components.ProductTable
import dorai.app.components.ProductTableRow
import dorai.app.theme.ProductStyle
import dorai.shared.roi.RoiOption
import dorai.shared.roi.RoiSpec
import kotlinx.coroutines.launch

@Composable
fun RegisterRoute(model: RegisterViewModel, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(model) { model.refresh() }
    RegisterScreen(
        model.state,
        model.editable,
        RegisterActions(
            refresh = { scope.launch { model.refresh() } },
            kind = model::selectKind,
            template = model::selectTemplate,
            new = model::startNew,
            edit = model::startEdit,
            type = model::type,
            save = { scope.launch { model.save() } },
            cancel = model::cancel,
            delete = { scope.launch { model.delete(it) } },
            cancelDelete = model::cancelDelete,
        ),
        modifier,
    )
}

/** Callbacks of the register screen; the screen never talks to the server itself. */
class RegisterActions(
    val refresh: () -> Unit,
    val kind: (ItemKind) -> Unit,
    val template: (String) -> Unit,
    val new: () -> Unit,
    val edit: (String) -> Unit,
    val type: (key: String, text: String) -> Unit,
    val save: () -> Unit,
    val cancel: () -> Unit,
    val delete: (String) -> Unit,
    val cancelDelete: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegisterScreen(state: RegisterState, editable: Boolean, actions: RegisterActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 64.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        PageHeading("Register items") {
            ProductButton(if (state.busy) "Loading..." else "Refresh", actions.refresh, outlined = true, enabled = !state.busy, icon = ProductIcon.REFRESH)
        }
        MetaText("The entity, providers, contracts and functions the register of information is built from. " +
            "Single RoI cells are set on the Register of Information page.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ItemKind.entries.forEach { ProductButton(it.title, { actions.kind(it) }, outlined = it != state.kind) }
        }
        if (state.kind == ItemKind.ROWS) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RegisterForms.handTemplates.forEach { ProductButton(it, { actions.template(it) }, outlined = it != state.template) }
            }
            MetaText(RoiSpec.template(state.template).name)
        }
        state.form?.let { FormPanel(it, state, actions) }
        state.message?.let { MetaText(it) }
        when {
            state.busy && state.register == null -> LoadingView("Loading the register...")
            state.error != null -> ErrorView(state.error, actions.refresh)
            state.register == null -> EmptyView("No register loaded", "")
            else -> ListPanel(state, editable, actions)
        }
    }
}

@Composable
private fun ListPanel(state: RegisterState, editable: Boolean, actions: RegisterActions) {
    ProductPanel {
        PanelHeading(state.kind.title, "${state.lines.size} in the register") {
            if (editable && state.kind != ItemKind.ENTITY) ProductButton("Add", actions.new, enabled = !state.busy)
        }
        if (state.lines.isEmpty()) {
            EmptyView("Nothing here yet", if (editable) "Add the first one." else "")
        } else {
            ProductTable(listOf("Item", "Details", ""), listOf(1f, 1.4f, 1.2f), minWidth = 760) {
                state.lines.forEach { line ->
                    ProductTableRow {
                        Column(Modifier.weight(1f)) { Text(line.title); if (line.title != line.id) MetaText(line.id) }
                        MetaText(line.detail, Modifier.weight(1.4f))
                        Row(Modifier.weight(1.2f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (editable) {
                                ProductButton("Edit", { actions.edit(line.id) }, outlined = true, enabled = !state.busy)
                                if (state.kind != ItemKind.ENTITY) {
                                    val asking = state.confirmDelete == line.id
                                    ProductButton(if (asking) "Confirm delete" else "Delete", { actions.delete(line.id) }, outlined = !asking, enabled = !state.busy)
                                    if (asking) ProductButton("Cancel", actions.cancelDelete, outlined = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormPanel(form: ItemForm, state: RegisterState, actions: RegisterActions) {
    ProductPanel(highlighted = true) {
        PanelHeading(form.template ?: form.kind.title, if (form.isNew) "New" else "Edit ${form.id}")
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            form.fields.forEach { field ->
                if (field.choices.isEmpty()) {
                    OutlinedTextField(field.text, { actions.type(field.key, it) }, Modifier.fillMaxWidth(),
                        label = { Text(field.label + if (field.required) " *" else "") }, singleLine = true, enabled = !state.busy)
                } else {
                    ChoiceField(field, enabled = !state.busy) { actions.type(field.key, it) }
                }
            }
            state.problems.forEach { MetaText(it, color = ProductStyle.colors.red) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ProductButton(if (state.busy) "Saving..." else "Save", actions.save, enabled = !state.busy)
                ProductButton("Cancel", actions.cancel, outlined = true, enabled = !state.busy)
            }
        }
    }
}

/**
 * A pick list with search: the field holds the option key; typing filters the options by key or label and
 * a click picks one. Long lists stay usable because only the matches show.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceField(field: FormField, enabled: Boolean, onPick: (String) -> Unit) {
    val picked = field.choices.firstOrNull { it.key == field.text.trim() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(field.text, onPick, Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
            label = { Text(field.label + if (field.required) " *" else "") },
            supportingText = { Text(picked?.label ?: "Type to search ${field.choices.size} options") })
        if (picked == null) {
            val matches = matching(field.choices, field.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                matches.take(MAX_SHOWN).forEach { option -> ProductButton("${option.key}: ${option.label}", { onPick(option.key) }, outlined = true, enabled = enabled) }
            }
            if (matches.size > MAX_SHOWN) MetaText("${matches.size - MAX_SHOWN} more; type more of the name to narrow the list.")
        }
    }
}

private const val MAX_SHOWN = 8

/** Options whose key or label holds every typed word, ignoring case. */
fun matching(options: List<RoiOption>, query: String): List<RoiOption> {
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    return options.filter { o -> words.all { w -> o.key.lowercase().contains(w) || o.label.lowercase().contains(w) } }
}
