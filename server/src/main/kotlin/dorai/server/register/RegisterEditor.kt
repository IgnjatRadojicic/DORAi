package dorai.server.register

import dorai.server.ApiProblem
import dorai.server.core.RegisterRepository
import dorai.shared.model.BusinessFunction
import dorai.shared.model.Citation
import dorai.shared.model.Contract
import dorai.shared.model.ContractService
import dorai.shared.model.FinancialEntity
import dorai.shared.model.Provenance
import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.Provider
import dorai.shared.model.Register
import dorai.shared.model.RegisterField
import dorai.shared.model.RoiFields
import dorai.shared.model.RoiRow
import dorai.shared.roi.RoiSpec
import io.ktor.http.HttpStatusCode
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Every change a person makes to the register. The server, not the client, sets provenance: a value that
 * changed becomes `human-confirmed` by [who] at the current time; a value sent back unchanged keeps the
 * provenance it had, so a client can neither fake "scanned" nor erase where a value came from.
 */
class RegisterEditor(private val repo: RegisterRepository, private val clock: Clock = Clock.System) {

    suspend fun putEntity(entity: FinancialEntity, who: String): FinancialEntity = edit { r, now ->
        checkColumns(entity.roi)
        val updated = entity.copy(roi = stampAll(r.entity.roi, entity.roi, now, who))
        r.copy(entity = updated) to updated
    }

    suspend fun putProvider(id: String, provider: Provider, who: String): Provider = edit { r, now ->
        sameId(id, provider.id)
        checkColumns(provider.roi)
        val old = r.providers.firstOrNull { it.id == id }
        val updated = provider.copy(
            lei = stamp(old?.lei, provider.lei, now, who),
            headquartersCountry = stamp(old?.headquartersCountry, provider.headquartersCountry, now, who),
            roi = stampAll(old?.roi, provider.roi, now, who),
        )
        r.copy(providers = r.providers.upsert(updated) { it.id }) to updated
    }

    suspend fun deleteProvider(id: String) = edit { r, _ ->
        if (r.contracts.any { it.providerId == id }) {
            throw ApiProblem(HttpStatusCode.Conflict, "Provider in use", "Provider $id still has contracts. Remove or move them first.")
        }
        r.copy(providers = r.providers.filterNot { it.id == id }.also { if (it.size == r.providers.size) notFound("provider", id) }) to Unit
    }

    suspend fun putContract(id: String, contract: Contract, who: String): Contract = edit { r, now ->
        sameId(id, contract.id)
        checkColumns(contract.roi)
        contract.services.forEach { checkColumns(it.roi) }
        val old = r.contracts.firstOrNull { it.id == id }
        val updated = contract.copy(
            annualCost = stamp(old?.annualCost, contract.annualCost, now, who),
            roi = stampAll(old?.roi, contract.roi, now, who),
            services = contract.services.map { s -> stampService(old?.services?.firstOrNull { it.id == s.id }, s, now, who) },
        )
        r.copy(contracts = r.contracts.upsert(updated) { it.id }) to updated
    }

    suspend fun deleteContract(id: String) = edit { r, _ ->
        r.copy(contracts = r.contracts.filterNot { it.id == id }.also { if (it.size == r.contracts.size) notFound("contract", id) }) to Unit
    }

    suspend fun putFunction(id: String, function: BusinessFunction, who: String): BusinessFunction = edit { r, now ->
        sameId(id, function.id)
        checkColumns(function.roi)
        val old = r.functions.firstOrNull { it.id == id }
        val updated = function.copy(roi = stampAll(old?.roi, function.roi, now, who))
        r.copy(functions = r.functions.upsert(updated) { it.id }) to updated
    }

    suspend fun deleteFunction(id: String) = edit { r, _ ->
        val used = r.contracts.flatMap { it.services }.any { it.functionId.value == id }
        if (used) throw ApiProblem(HttpStatusCode.Conflict, "Function in use", "Services still support function $id.")
        r.copy(functions = r.functions.filterNot { it.id == id }.also { if (it.size == r.functions.size) notFound("function", id) }) to Unit
    }

    suspend fun putRow(template: String, id: String, row: RoiRow, who: String): RoiRow = edit { r, now ->
        sameId(id, row.id)
        if (RoiSpec.templates.none { it.code == template }) bad("Unknown template $template.")
        row.fields.keys.firstOrNull { !it.startsWith("$template.") }?.let { bad("Column $it does not belong to $template.") }
        checkColumns(row.fields)
        val rows = r.rows[template].orEmpty()
        val updated = row.copy(fields = stampAll(rows.firstOrNull { it.id == id }?.fields, row.fields, now, who))
        r.copy(rows = r.rows + (template to rows.upsert(updated) { it.id })) to updated
    }

    suspend fun deleteRow(template: String, id: String) = edit { r, _ ->
        val rows = r.rows[template].orEmpty()
        if (rows.none { it.id == id }) notFound("row", "$template/$id")
        r.copy(rows = r.rows + (template to rows.filterNot { it.id == id })) to Unit
    }

    /**
     * Writes an accepted AI value into the item it was for: a service, contract, provider, function or the
     * entity (by LEI). The value becomes `human-confirmed`, keeping the AI's citation and reason.
     */
    suspend fun acceptValue(itemId: String, column: String, value: String, citation: Citation?, reason: String, who: String) =
        writeValue(itemId, column) { now -> RegisterField(value, Provenance(ProvenanceLabel.HumanConfirmed, now, citation, "Accepted by $who: $reason", confirmedBy = who)) }

    /** One cell typed by a person; blank clears the cell so the derived or scanned value shows again. */
    suspend fun setValue(itemId: String, column: String, value: String?, who: String) =
        writeValue(itemId, column) { now -> value?.trim()?.ifEmpty { null }?.let { RegisterField(it, Provenance(ProvenanceLabel.HumanConfirmed, now, reason = "Set by $who", confirmedBy = who)) } }

    private suspend fun writeValue(itemId: String, column: String, make: (Instant) -> RegisterField?) = edit { r, now ->
        if (RoiSpec.column(column) == null) bad("Unknown RoI column $column.")
        val field = make(now)
        fun RoiFields.with() = if (field == null) this - column else this + (column to field)
        val updated = when {
            r.entity.lei == itemId -> r.copy(entity = r.entity.copy(roi = r.entity.roi.with()))
            r.providers.any { it.id == itemId } -> r.copy(providers = r.providers.map { if (it.id == itemId) it.copy(roi = it.roi.with()) else it })
            r.functions.any { it.id == itemId } -> r.copy(functions = r.functions.map { if (it.id == itemId) it.copy(roi = it.roi.with()) else it })
            r.contracts.any { it.id == itemId } -> r.copy(contracts = r.contracts.map { if (it.id == itemId) it.copy(roi = it.roi.with()) else it })
            r.contracts.any { c -> c.services.any { it.id == itemId } } -> r.copy(
                contracts = r.contracts.map { c ->
                    c.copy(services = c.services.map { if (it.id == itemId) it.copy(roi = it.roi.with()) else it })
                },
            )
            else -> notFound("register item", itemId)
        }
        updated to Unit
    }

    private suspend fun <T> edit(change: (Register, Instant) -> Pair<Register, T>): T {
        val now = clock.now()
        return repo.updateRegister { change(it, now) }
    }

    private fun stampService(old: ContractService?, new: ContractService, now: Instant, who: String) = new.copy(
        serviceType = stamp(old?.serviceType, new.serviceType, now, who),
        functionId = stamp(old?.functionId, new.functionId, now, who),
        dataLocation = stamp(old?.dataLocation, new.dataLocation, now, who),
        roi = stampAll(old?.roi, new.roi, now, who),
    )

    private fun checkColumns(fields: RoiFields) {
        fields.keys.firstOrNull { RoiSpec.column(it) == null }?.let { bad("Unknown RoI column $it.") }
    }

    private fun sameId(path: String, body: String) {
        if (path != body) bad("The id in the path ($path) and in the body ($body) differ.")
    }

    private fun bad(detail: String): Nothing = throw ApiProblem(HttpStatusCode.BadRequest, "Invalid register edit", detail)
    private fun notFound(what: String, id: String): Nothing = throw ApiProblem(HttpStatusCode.NotFound, "Not found", "No $what $id.")

    companion object {
        /** Keeps the stored field when the value did not change; otherwise a person set it now. */
        fun stamp(old: RegisterField?, new: RegisterField, now: Instant, who: String): RegisterField =
            if (old != null && old.value == new.value) old
            else RegisterField(new.value, Provenance(ProvenanceLabel.HumanConfirmed, now, reason = "Set by $who", confirmedBy = who))

        fun stampAll(old: RoiFields?, new: RoiFields, now: Instant, who: String): RoiFields =
            new.mapValues { (code, f) -> stamp(old?.get(code), f, now, who) }.filterValues { !it.value.isNullOrBlank() }
    }
}

private fun <T> List<T>.upsert(item: T, key: (T) -> String): List<T> =
    if (any { key(it) == key(item) }) map { if (key(it) == key(item)) item else it } else this + item
