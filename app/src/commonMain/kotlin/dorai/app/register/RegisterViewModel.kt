package dorai.app.register

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dorai.app.components.displayMessage
import dorai.shared.model.BusinessFunction
import dorai.shared.model.Contract
import dorai.shared.model.FinancialEntity
import dorai.shared.model.Provider
import dorai.shared.model.Register
import dorai.shared.model.RoiRow
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

/** The register calls the forms need; the server stamps who saved each value. */
interface RegisterSource {
    suspend fun load(): Register
    suspend fun saveEntity(entity: FinancialEntity)
    suspend fun saveProvider(provider: Provider)
    suspend fun deleteProvider(id: String)
    suspend fun saveContract(contract: Contract)
    suspend fun deleteContract(id: String)
    suspend fun saveFunction(function: BusinessFunction)
    suspend fun deleteFunction(id: String)
    suspend fun saveRow(template: String, row: RoiRow)
    suspend fun deleteRow(template: String, id: String)
}

/** One line of the list on the left: the item's id and what to show for it. */
data class ItemLine(val id: String, val title: String, val detail: String)

data class RegisterState(
    val busy: Boolean = false,
    val register: Register? = null,
    val kind: ItemKind = ItemKind.PROVIDERS,
    val template: String = RegisterForms.handTemplates.first(),
    val form: ItemForm? = null,
    val problems: List<String> = emptyList(),
    /** Id of an item waiting for the second click on Delete. */
    val confirmDelete: String? = null,
    /** True while the screen asks whether to throw away a changed form. */
    val askDiscard: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val lines: List<ItemLine>
        get() {
            val r = register ?: return emptyList()
            return when (kind) {
                ItemKind.ENTITY -> listOf(ItemLine(r.entity.lei, r.entity.name, "${r.entity.country}  LEI ${r.entity.lei}"))
                ItemKind.PROVIDERS -> r.providers.map { ItemLine(it.id, it.name, listOfNotNull(it.headquartersCountry.value, it.lei.value?.let { l -> "LEI $l" }).joinToString("  ")) }
                ItemKind.CONTRACTS -> r.contracts.map { c ->
                    ItemLine(c.id, c.id, "${r.providers.firstOrNull { it.id == c.providerId }?.name ?: c.providerId}  from ${c.startDate}")
                }
                ItemKind.FUNCTIONS -> r.functions.map { ItemLine(it.id, it.name, if (it.isCritical) "critical or important" else "") }
                ItemKind.ROWS -> r.rows[template].orEmpty().map { row ->
                    ItemLine(row.id, row.id, row.fields.values.mapNotNull { it.value }.take(3).joinToString("  "))
                }
            }
        }
}

/**
 * Create, edit and delete register items by hand: the entity, providers, contracts, functions and the rows of
 * templates without an item of their own. Viewers only read (the server refuses their writes as well).
 */
class RegisterViewModel(
    private val source: RegisterSource,
    private val canEdit: () -> Boolean,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    var state by mutableStateOf(RegisterState())
        private set

    val editable: Boolean get() = canEdit()

    /** The form as it was opened; a form that differs from it has changes the person would lose. */
    private var opened: ItemForm? = null

    /** What the person asked for while a changed form was open; it runs once they agree to lose the changes. */
    private var afterDiscard: (() -> Unit)? = null

    private val dirty: Boolean get() = state.form != null && state.form != opened

    /** Runs [action] at once, or first asks when it would throw away a changed form. */
    private fun leaveForm(action: () -> Unit) {
        if (!dirty) return action()
        afterDiscard = action
        state = state.copy(askDiscard = true)
    }

    fun keepEditing() {
        afterDiscard = null
        state = state.copy(askDiscard = false)
    }

    fun discardChanges() {
        val action = afterDiscard
        afterDiscard = null
        opened = state.form
        state = state.copy(askDiscard = false)
        action?.invoke()
    }

    suspend fun refresh() {
        if (state.busy) return
        state = state.copy(busy = true, error = null)
        state = try {
            state.copy(busy = false, register = source.load())
        } catch (cancelled: CancellationException) {
            state = state.copy(busy = false)
            throw cancelled
        } catch (error: Exception) {
            state.copy(busy = false, error = error.displayMessage("The register could not be loaded. Check the connection and retry."))
        }
    }

    fun selectKind(kind: ItemKind) = leaveForm {
        state = state.copy(kind = kind, form = null, problems = emptyList(), confirmDelete = null, message = null)
    }

    fun selectTemplate(template: String) {
        if (template in RegisterForms.handTemplates) leaveForm {
            state = state.copy(template = template, form = null, problems = emptyList(), confirmDelete = null)
        }
    }

    fun startNew() {
        val register = state.register ?: return
        if (!canEdit() || state.kind == ItemKind.ENTITY) return
        leaveForm { open(formFor(register, null)) }
    }

    fun startEdit(id: String) {
        val register = state.register ?: return
        if (!canEdit()) return
        leaveForm { open(formFor(register, id)) }
    }

    private fun open(form: ItemForm) {
        opened = form
        state = state.copy(form = form, problems = emptyList(), message = null, confirmDelete = null)
    }

    fun type(key: String, text: String) { state.form?.let { state = state.copy(form = it.with(key, text), problems = emptyList()) } }

    fun cancel() { state = state.copy(form = null, problems = emptyList()) }

    suspend fun save() {
        val form = state.form ?: return
        val r = state.register ?: return
        val problems = RegisterForms.problems(form, taken(r, form))
        if (problems.isNotEmpty()) {
            state = state.copy(problems = problems)
            return
        }
        val now = clock.now()
        write("The item could not be saved.", "Saved.") {
            when (form.kind) {
                ItemKind.ENTITY -> source.saveEntity(RegisterForms.toEntity(form, r.entity))
                ItemKind.PROVIDERS -> source.saveProvider(RegisterForms.toProvider(form, r.providers.firstOrNull { it.id == form.id }, now))
                ItemKind.CONTRACTS -> source.saveContract(RegisterForms.toContract(form, r.contracts.firstOrNull { it.id == form.id }, now))
                ItemKind.FUNCTIONS -> source.saveFunction(RegisterForms.toFunction(form, r.functions.firstOrNull { it.id == form.id }))
                ItemKind.ROWS -> {
                    val template = checkNotNull(form.template)
                    source.saveRow(template, RegisterForms.toRow(form, r.rows[template]?.firstOrNull { it.id == form.id }, now))
                }
            }
        }
    }

    /** First click asks, second click deletes. The entity can't be deleted. */
    suspend fun delete(id: String) {
        if (!canEdit() || state.kind == ItemKind.ENTITY) return
        if (state.confirmDelete != id) {
            state = state.copy(confirmDelete = id, message = null)
            return
        }
        write("The item could not be deleted.", "Deleted $id.") {
            when (state.kind) {
                ItemKind.PROVIDERS -> source.deleteProvider(id)
                ItemKind.CONTRACTS -> source.deleteContract(id)
                ItemKind.FUNCTIONS -> source.deleteFunction(id)
                ItemKind.ROWS -> source.deleteRow(state.template, id)
                ItemKind.ENTITY -> Unit
            }
        }
    }

    fun cancelDelete() { state = state.copy(confirmDelete = null) }

    private suspend fun write(fallback: String, done: String, block: suspend () -> Unit) {
        if (state.busy) return
        state = state.copy(busy = true, message = null)
        try {
            block()
            state = state.copy(busy = false, register = source.load(), form = null, confirmDelete = null, message = done)
        } catch (cancelled: CancellationException) {
            state = state.copy(busy = false)
            throw cancelled
        } catch (error: Exception) {
            state = state.copy(busy = false, confirmDelete = null, message = error.displayMessage(fallback))
        }
    }

    private fun formFor(r: Register, id: String?): ItemForm = when (state.kind) {
        ItemKind.ENTITY -> RegisterForms.entity(r.entity)
        ItemKind.PROVIDERS -> RegisterForms.provider(r.providers.firstOrNull { it.id == id })
        ItemKind.CONTRACTS -> RegisterForms.contract(r.contracts.firstOrNull { it.id == id }, r)
        ItemKind.FUNCTIONS -> RegisterForms.function(r.functions.firstOrNull { it.id == id })
        ItemKind.ROWS -> RegisterForms.row(state.template, r.rows[state.template]?.firstOrNull { it.id == id })
    }

    private fun taken(r: Register, form: ItemForm): Set<String> = when (form.kind) {
        ItemKind.ENTITY -> emptySet()
        ItemKind.PROVIDERS -> r.providers.map { it.id }.toSet()
        ItemKind.CONTRACTS -> r.contracts.map { it.id }.toSet()
        ItemKind.FUNCTIONS -> r.functions.map { it.id }.toSet()
        ItemKind.ROWS -> r.rows[form.template].orEmpty().map { it.id }.toSet()
    }
}
