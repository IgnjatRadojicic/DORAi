package dorai.server.db

import dorai.shared.model.ChangeAction
import dorai.shared.model.Citation
import dorai.shared.model.DoraiJson
import dorai.shared.model.Provenance
import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.Redundancy
import dorai.shared.model.RegisterField
import dorai.shared.model.ResourceSource
import dorai.shared.model.ServiceType
import dorai.shared.model.SuggestionKind
import dorai.shared.model.SuggestionStatus
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestamp

// Mirrors db/migration. Flyway owns the schema; these objects only describe it for queries.

object FinancialEntities : Table("financial_entities") {
    val lei = text("lei")
    val name = text("name")
    val country = text("country")
    val asOf = timestamp("as_of")
    val roi = jsonb("roi")
    override val primaryKey = PrimaryKey(lei)
}

object Providers : Table("providers") {
    val id = text("id")
    val position = integer("position")
    val name = text("name")
    val lei = FieldColumns(this, "lei")
    val headquartersCountry = FieldColumns(this, "headquarters_country")
    val roi = jsonb("roi")
    override val primaryKey = PrimaryKey(id)
}

object BusinessFunctions : Table("business_functions") {
    val id = text("id")
    val position = integer("position")
    val name = text("name")
    val isCritical = bool("is_critical")
    val roi = jsonb("roi")
    override val primaryKey = PrimaryKey(id)
}

object Contracts : Table("contracts") {
    val id = text("id")
    val position = integer("position")
    val providerId = text("provider_id")
    val startDate = date("start_date")
    val endDate = date("end_date").nullable()
    val annualCost = FieldColumns(this, "annual_cost")
    val currency = text("currency").nullable()
    val roi = jsonb("roi")
    override val primaryKey = PrimaryKey(id)
}

object ContractServices
 : Table("contract_services") {
    val id = text("id")
    val contractId = text("contract_id")
    val position = integer("position")
    val serviceType = FieldColumns(this, "service_type")
    val functionId = FieldColumns(this, "function_id")
    val dataLocation = FieldColumns(this, "data_location")
    val resourceIds = array<String>("resource_ids")
    val roi = jsonb("roi")
    override val primaryKey = PrimaryKey(id)
}

object Resources : Table("resources") {
    val id = text("id")
    val position = integer("position")
    val name = text("name")
    val type = text("type")
    val provider = text("provider")
    val location = text("location")
    val country = text("country").nullable()
    /** Named so it does not hide ColumnSet.source. */
    val resourceSource = enumerationByName<ResourceSource>("source", 32)
    val changeAction = enumerationByName<ChangeAction>("change_action", 32)
    val tags = jsonb("tags")
    val serviceType = enumerationByName<ServiceType>("service_type", 32).nullable()
    val redundancy = enumerationByName<Redundancy>("redundancy", 32)
    val previousRedundancy = enumerationByName<Redundancy>("previous_redundancy", 32).nullable()
    val functionId = text("function_id").nullable()
    val contractId = text("contract_id").nullable()
    val monthlyCost = double("monthly_cost").nullable()
    val currency = text("currency").nullable()
    val replicaCountries = array<String>("replica_countries")
    val backup = jsonb("backup").nullable()
    val alerts = jsonb("alerts")
    override val primaryKey = PrimaryKey(id)
}

object CostEntries : Table("cost_entries") {
    val resourceId = text("resource_id")
    val month = date("month")
    val position = integer("position")
    val provider = text("provider")
    val amount = double("amount")
    val currency = text("currency")
    override val primaryKey = PrimaryKey(resourceId, month)
}

object RoiRows : Table("roi_rows") {
    val template = text("template")
    val id = text("id")
    val position = integer("position")
    /** Named so it does not hide ColumnSet.fields. */
    val values = jsonb("fields")
    override val primaryKey = PrimaryKey(template, id)
}

object Suggestions : Table("suggestions") {
    val id = text("id")
    /** Insertion order, filled by the database. */
    val seq = long("seq").autoIncrement()
    val resourceId = text("resource_id")
    val resourceName = text("resource_name")
    val kind = enumerationByName<SuggestionKind>("kind", 32)
    val value = text("value")
    val reason = text("reason")
    val citations = jsonb("citations")
    val confidence = double("confidence").nullable()
    val status = enumerationByName<SuggestionStatus>("status", 32)
    val createdAt = timestamp("created_at")
    val decidedAt = timestamp("decided_at").nullable()
    val roiColumn = text("roi_column").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** The five columns of one register field: its value and its provenance. */
class FieldColumns(table: Table, name: String) {
    // nullable() is a member of Table, hence with(table).
    val value = with(table) { text(name).nullable() }
    val label = table.enumerationByName<ProvenanceLabel>("${name}_label", 32)
    val updatedAt = table.timestamp("${name}_updated_at")
    val citation = with(table) { jsonb("${name}_citation").nullable() }
    val reason = with(table) { text("${name}_reason").nullable() }

    fun read(row: ResultRow) = RegisterField(
        value = row[value],
        provenance = Provenance(
            label = row[label],
            updatedAt = row[updatedAt],
            citation = row[citation]?.let { DoraiJson.decodeFromString<Citation>(it) },
            reason = row[reason],
        ),
    )

    fun write(to: UpdateBuilder<*>, field: RegisterField) {
        to[value] = field.value
        to[label] = field.provenance.label
        to[updatedAt] = field.provenance.updatedAt
        to[citation] = field.provenance.citation?.let { DoraiJson.encodeToString(it) }
        to[reason] = field.provenance.reason
    }
}

/** A jsonb column holding JSON text; the value is cast to jsonb in SQL, so no driver types are needed. */
class JsonbColumnType : ColumnType<String>() {
    override fun sqlType() = "JSONB"
    override fun valueFromDB(value: Any): String = value.toString()
    override fun parameterMarker(value: String?) = "?::jsonb"
}

fun Table.jsonb(name: String) = registerColumn(name, JsonbColumnType())
