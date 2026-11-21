package dorai.app.roi

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dorai.app.components.displayMessage
import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.ValidationSeverity
import dorai.shared.roi.RoiItems
import dorai.shared.roi.RoiSpec
import dorai.shared.roi.RoiView
import kotlinx.coroutines.CancellationException

/** The RoI as the server builds it, and one cell write. */
interface RoiSource {
    suspend fun load(): RoiView
    suspend fun setValue(target: RoiItems.Target, column: String, value: String?)
}

/** One template's readiness for the overview. */
data class TemplateStatus(val code: String, val name: String, val rows: Int, val errors: Int, val warnings: Int)

/** A cell being edited. */
data class CellEdit(val template: String, val rowKey: String, val column: String, val text: String)

data class RoiState(
    val busy: Boolean = false,
    val view: RoiView? = null,
    val template: String = "B_02.02",
    val edit: CellEdit? = null,
    val saving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val templates: List<TemplateStatus>
        get() = RoiSpec.templates.map { t ->
            val issues = view?.issues.orEmpty().filter { it.template == t.code }
            TemplateStatus(t.code, t.name, view?.tables?.get(t.code)?.size ?: 0,
                issues.count { it.severity == ValidationSeverity.Error }, issues.count { it.severity == ValidationSeverity.Warning })
        }
}

/**
 * The register as the 15 RoI templates. A person may set a cell of a register item (entity, contract,
 * service, provider, function); derived and scanned cells are changed where they come from.
 */
class RoiViewModel(private val source: RoiSource, private val canEdit: () -> Boolean) : ViewModel() {
    var state by mutableStateOf(RoiState())
        private set

    suspend fun refresh() {
        if (state.busy) return
        state = state.copy(busy = true, error = null)
        state = try {
            state.copy(busy = false, view = source.load())
        } catch (cancelled: CancellationException) {
            state = state.copy(busy = false)
            throw cancelled
        } catch (error: Exception) {
            state.copy(busy = false, view = null, error = error.displayMessage("The register could not be loaded. Check the connection and retry."))
        }
    }

    fun select(template: String) {
        if (RoiSpec.templates.any { it.code == template }) state = state.copy(template = template, edit = null, message = null)
    }

    /** Only cells of a register item that are empty or set by a person can be edited here. */
    fun editable(template: String, rowKey: String, column: String): Boolean {
        if (!canEdit() || RoiItems.target(template, rowKey) == null) return false
        val cell = state.view?.tables?.get(template)?.firstOrNull { it.key == rowKey }?.cells?.get(column)
        return cell == null || cell.label == ProvenanceLabel.HumanConfirmed || cell.label == ProvenanceLabel.AiSuggested || cell.label == ProvenanceLabel.Planned
    }

    fun startEdit(rowKey: String, column: String) {
        val template = state.template
        if (!editable(template, rowKey, column)) return
        val current = state.view?.tables?.get(template)?.firstOrNull { it.key == rowKey }?.cells?.get(column)?.value.orEmpty()
        state = state.copy(edit = CellEdit(template, rowKey, column, current), message = null)
    }

    fun type(text: String) { state.edit?.let { state = state.copy(edit = it.copy(text = text)) } }

    fun cancel() { state = state.copy(edit = null) }

    suspend fun save() {
        val edit = state.edit ?: return
        val target = RoiItems.target(edit.template, edit.rowKey) ?: return
        state = state.copy(saving = true, message = null)
        try {
            source.setValue(target, edit.column, edit.text.ifBlank { null })
            val view = source.load()
            val now = view.tables[edit.template]?.firstOrNull { it.key == edit.rowKey }?.cells?.get(edit.column)?.value
            val wanted = edit.text.trim().ifEmpty { null }
            state = state.copy(
                saving = false, edit = null, view = view,
                message = if (wanted != null && now != wanted) "Saved, but ${edit.column} still shows the value from the register item itself; change it there." else "Saved ${edit.column}.",
            )
        } catch (cancelled: CancellationException) {
            state = state.copy(saving = false)
            throw cancelled
        } catch (error: Exception) {
            state = state.copy(saving = false, message = error.displayMessage("The value could not be saved."))
        }
    }
}
