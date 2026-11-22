package dorai.server.db

import dorai.server.core.CheckedPlan
import dorai.server.core.CostEntry
import dorai.server.core.PlanRepository
import dorai.server.core.RegisterRepository
import dorai.server.core.SuggestionRepository
import dorai.server.core.SuggestionRepository.Decision
import dorai.shared.model.BusinessFunction
import dorai.shared.model.Citation
import dorai.shared.model.Contract
import dorai.shared.model.ContractService
import dorai.shared.model.DoraiJson
import dorai.shared.model.FinancialEntity
import dorai.shared.model.Provider
import dorai.shared.model.BackupInfo
import dorai.shared.model.SecurityAlert
import dorai.shared.model.RoiFields
import dorai.shared.model.RoiRow
import dorai.shared.model.Register
import dorai.shared.model.Resource
import dorai.shared.model.Suggestion
import dorai.shared.model.SuggestionStatus
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Clock

class PostgresRegisterRepository(private val db: Database) : RegisterRepository {

    override suspend fun register(): Register = db.tx { readRegister() }

    override suspend fun resources(): List<Resource> = db.tx {
        Resources.selectAll().orderBy(Resources.position).map { it.toResource() }
    }

    override suspend fun costs(): List<CostEntry> = db.tx {
        CostEntries.selectAll().orderBy(CostEntries.position).map {
            CostEntry(it[CostEntries.resourceId], it[CostEntries.provider], it[CostEntries.month], it[CostEntries.amount], it[CostEntries.currency])
        }
    }

    override suspend fun saveScan(resources: List<Resource>, costs: List<CostEntry>) = db.tx { writeScan(resources, costs) }

    override suspend fun <T> updateRegister(change: (Register) -> Pair<Register, T>): T = db.tx {
        // Locks the entity row, so two edits at once run one after the other.
        FinancialEntities.selectAll().forUpdate().toList()
        val (updated, result) = change(readRegister())
        deleteRegister()
        writeRegister(updated)
        result
    }
}

/** Every checked plan is kept (the history shows what was checked when); reports read the latest. */
class PostgresPlanRepository(private val db: Database) : PlanRepository {
    override suspend fun latest(): CheckedPlan? = db.tx {
        CheckedPlans.selectAll().orderBy(CheckedPlans.id, SortOrder.DESC).limit(1).singleOrNull()?.let {
            CheckedPlan(it[CheckedPlans.planJson], it[CheckedPlans.checkedBy], it[CheckedPlans.checkedAt])
        }
    }

    override suspend fun save(plan: CheckedPlan) = db.tx {
        CheckedPlans.insert {
            it[planJson] = plan.json
            it[checkedBy] = plan.checkedBy
            it[checkedAt] = plan.checkedAt
        }
        Unit
    }
}

class PostgresSuggestionRepository(private val db: Database, private val clock: Clock = Clock.System) : SuggestionRepository {

    override suspend fun all(): List<Suggestion> = db.tx {
        Suggestions.selectAll().orderBy(Suggestions.seq).map { it.toSuggestion() }
    }

    override suspend fun pending(resourceId: String?): List<Suggestion> = db.tx {
        Suggestions.selectAll()
            .where {
                val isPending = Suggestions.status eq SuggestionStatus.Pending
                if (resourceId == null) isPending else isPending and (Suggestions.resourceId eq resourceId)
            }
            .orderBy(Suggestions.seq)
            .map { it.toSuggestion() }
    }

    override suspend fun add(suggestions: List<Suggestion>) = db.tx { writeSuggestions(suggestions) }

    override suspend fun decide(id: String, status: SuggestionStatus, by: String): Decision = db.tx {
        // FOR UPDATE: two reviewers deciding at once wait for each other, so only one wins.
        val current = Suggestions.selectAll().where { Suggestions.id eq id }.forUpdate().singleOrNull()?.toSuggestion()
            ?: return@tx Decision.NotFound
        if (current.status != SuggestionStatus.Pending) return@tx Decision.AlreadyDecided(current)
        val decidedAt = clock.now()
        Suggestions.update({ Suggestions.id eq id }) {
            it[Suggestions.status] = status
            it[Suggestions.decidedAt] = decidedAt
            it[Suggestions.decidedBy] = by
        }
        // Read back, so the answer carries the timestamp as stored (Postgres keeps microseconds).
        Decision.Done(Suggestions.selectAll().where { Suggestions.id eq id }.single().toSuggestion())
    }
}

// Reads

internal fun JdbcTransaction.readRegister(): Register {
    val entity = FinancialEntities.selectAll().singleOrNull() ?: error("The register is empty: no financial entity is stored.")
    val services = ContractServices.selectAll().orderBy(ContractServices.position).groupBy({ it[ContractServices.contractId] }) {
        ContractService(
            id = it[ContractServices.id],
            serviceType = ContractServices.serviceType.read(it),
            functionId = ContractServices.functionId.read(it),
            dataLocation = ContractServices.dataLocation.read(it),
            resourceIds = it[ContractServices.resourceIds],
            roi = readRoi(it[ContractServices.roi]),
        )
    }
    return Register(
        entity = FinancialEntity(
            entity[FinancialEntities.lei], entity[FinancialEntities.name], entity[FinancialEntities.country], readRoi(entity[FinancialEntities.roi]),
        ),
        providers = Providers.selectAll().orderBy(Providers.position).map {
            Provider(it[Providers.id], it[Providers.name], Providers.lei.read(it), Providers.headquartersCountry.read(it), readRoi(it[Providers.roi]))
        },
        contracts = Contracts.selectAll().orderBy(Contracts.position).map {
            Contract(
                id = it[Contracts.id],
                providerId = it[Contracts.providerId],
                startDate = it[Contracts.startDate],
                endDate = it[Contracts.endDate],
                annualCost = Contracts.annualCost.read(it),
                currency = it[Contracts.currency],
                services = services[it[Contracts.id]].orEmpty(),
                roi = readRoi(it[Contracts.roi]),
            )
        },
        functions = BusinessFunctions.selectAll().orderBy(BusinessFunctions.position).map {
            BusinessFunction(it[BusinessFunctions.id], it[BusinessFunctions.name], it[BusinessFunctions.isCritical], readRoi(it[BusinessFunctions.roi]))
        },
        asOf = entity[FinancialEntities.asOf],
        rows = RoiRows.selectAll().orderBy(RoiRows.position).groupBy({ it[RoiRows.template] }) {
            RoiRow(it[RoiRows.id], readRoi(it[RoiRows.values]))
        },
    )
}

private fun readRoi(json: String): RoiFields = DoraiJson.decodeFromString(json)

private fun writeRoi(fields: RoiFields): String = DoraiJson.encodeToString(fields)

private fun ResultRow.toResource() = Resource(
    id = this[Resources.id],
    name = this[Resources.name],
    type = this[Resources.type],
    provider = this[Resources.provider],
    location = this[Resources.location],
    country = this[Resources.country],
    source = this[Resources.resourceSource],
    changeAction = this[Resources.changeAction],
    tags = DoraiJson.decodeFromString<Map<String, String>>(this[Resources.tags]),
    serviceType = this[Resources.serviceType],
    redundancy = this[Resources.redundancy],
    previousRedundancy = this[Resources.previousRedundancy],
    functionId = this[Resources.functionId],
    contractId = this[Resources.contractId],
    monthlyCost = this[Resources.monthlyCost],
    currency = this[Resources.currency],
    replicaCountries = this[Resources.replicaCountries],
    backup = this[Resources.backup]?.let { DoraiJson.decodeFromString<BackupInfo>(it) },
    alerts = DoraiJson.decodeFromString<List<SecurityAlert>>(this[Resources.alerts]),
)

private fun ResultRow.toSuggestion() = Suggestion(
    id = this[Suggestions.id],
    resourceId = this[Suggestions.resourceId],
    resourceName = this[Suggestions.resourceName],
    kind = this[Suggestions.kind],
    value = this[Suggestions.value],
    reason = this[Suggestions.reason],
    citations = DoraiJson.decodeFromString<List<Citation>>(this[Suggestions.citations]),
    confidence = this[Suggestions.confidence],
    status = this[Suggestions.status],
    createdAt = this[Suggestions.createdAt],
    decidedAt = this[Suggestions.decidedAt],
    decidedBy = this[Suggestions.decidedBy],
    column = this[Suggestions.roiColumn],
)

// Writes

/** Removes the stored register (not the scan or suggestions), before an edited one is written back. */
internal fun JdbcTransaction.deleteRegister() {
    RoiRows.deleteAll()
    ContractServices.deleteAll()
    Contracts.deleteAll()
    BusinessFunctions.deleteAll()
    Providers.deleteAll()
    FinancialEntities.deleteAll()
}

/** Stores a whole register into empty register tables: the seed, or an edit after [deleteRegister]. */
internal fun JdbcTransaction.writeRegister(register: Register) {
    FinancialEntities.insert {
        it[lei] = register.entity.lei
        it[name] = register.entity.name
        it[country] = register.entity.country
        it[asOf] = register.asOf
        it[roi] = writeRoi(register.entity.roi)
    }
    register.providers.forEachIndexed { i, p ->
        Providers.insert {
            it[id] = p.id
            it[position] = i
            it[name] = p.name
            lei.write(it, p.lei)
            headquartersCountry.write(it, p.headquartersCountry)
            it[roi] = writeRoi(p.roi)
        }
    }
    register.functions.forEachIndexed { i, f ->
        BusinessFunctions.insert {
            it[id] = f.id
            it[position] = i
            it[name] = f.name
            it[isCritical] = f.isCritical
            it[roi] = writeRoi(f.roi)
        }
    }
    var servicePosition = 0
    register.contracts.forEachIndexed { i, c ->
        Contracts.insert {
            it[id] = c.id
            it[position] = i
            it[providerId] = c.providerId
            it[startDate] = c.startDate
            it[endDate] = c.endDate
            annualCost.write(it, c.annualCost)
            it[currency] = c.currency
            it[roi] = writeRoi(c.roi)
        }
        c.services.forEach { s ->
            ContractServices.insert {
                it[id] = s.id
                it[contractId] = c.id
                it[position] = servicePosition++
                serviceType.write(it, s.serviceType)
                functionId.write(it, s.functionId)
                dataLocation.write(it, s.dataLocation)
                it[resourceIds] = s.resourceIds
                it[roi] = writeRoi(s.roi)
            }
        }
    }
    register.rows.forEach { (template, rows) ->
        rows.forEachIndexed { i, r ->
            RoiRows.insert {
                it[RoiRows.template] = template
                it[id] = r.id
                it[position] = i
                it[values] = writeRoi(r.fields)
            }
        }
    }
}

/** Replaces the stored scan: resources and their costs. */
internal fun JdbcTransaction.writeScan(resources: List<Resource>, costs: List<CostEntry>) {
    CostEntries.deleteAll()
    Resources.deleteAll()
    resources.forEachIndexed { i, r ->
        Resources.insert {
            it[id] = r.id
            it[position] = i
            it[name] = r.name
            it[type] = r.type
            it[provider] = r.provider
            it[location] = r.location
            it[country] = r.country
            it[resourceSource] = r.source
            it[changeAction] = r.changeAction
            it[tags] = DoraiJson.encodeToString(r.tags)
            it[serviceType] = r.serviceType
            it[redundancy] = r.redundancy
            it[previousRedundancy] = r.previousRedundancy
            it[functionId] = r.functionId
            it[contractId] = r.contractId
            it[monthlyCost] = r.monthlyCost
            it[currency] = r.currency
            it[replicaCountries] = r.replicaCountries
            it[backup] = r.backup?.let { b -> DoraiJson.encodeToString(b) }
            it[alerts] = DoraiJson.encodeToString(r.alerts)
        }
    }
    costs.forEachIndexed { i, c ->
        CostEntries.insert {
            it[resourceId] = c.resourceId
            it[month] = c.month
            it[position] = i
            it[provider] = c.provider
            it[amount] = c.amount
            it[currency] = c.currency
        }
    }
}

/** Adds suggestions; one with an existing id replaces it and keeps its place in the order. */
internal fun JdbcTransaction.writeSuggestions(suggestions: List<Suggestion>) {
    suggestions.forEach { s ->
        Suggestions.upsert(onUpdateExclude = listOf(Suggestions.seq)) {
            it[id] = s.id
            it[resourceId] = s.resourceId
            it[resourceName] = s.resourceName
            it[kind] = s.kind
            it[value] = s.value
            it[reason] = s.reason
            it[citations] = DoraiJson.encodeToString(s.citations)
            it[confidence] = s.confidence
            it[status] = s.status
            it[createdAt] = s.createdAt
            it[decidedAt] = s.decidedAt
            it[decidedBy] = s.decidedBy
            it[roiColumn] = s.column
        }
    }
}

/** True when no register is stored yet. */
internal fun JdbcTransaction.registerIsEmpty(): Boolean = FinancialEntities.selectAll().empty()
