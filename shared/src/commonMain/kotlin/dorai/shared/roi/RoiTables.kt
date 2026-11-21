package dorai.shared.roi

import dorai.shared.model.ContractService
import dorai.shared.model.Contract
import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.Provider
import dorai.shared.model.Register
import dorai.shared.model.RegisterField
import dorai.shared.model.Resource
import dorai.shared.model.RoiFields
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

/**
 * One filled cell. [label] is the provenance of the register value it came from, or null when DORAi worked
 * it out from other values (a sum, a join, a CIR default such as `9999-12-31` for "no end date").
 */
@Serializable
data class RoiCell(val value: String, val label: ProvenanceLabel?)

/** Facts about a row that the CIR's "mandatory if" conditions depend on and that are not cells of the row. */
@Serializable
data class RowFacts(
    val critical: Boolean = false,
    val processesData: Boolean = false,
    val terminated: Boolean = false,
    val directProvider: Boolean = false,
)

@Serializable
data class RoiRowOut(val key: String, val cells: Map<String, RoiCell>, val facts: RowFacts = RowFacts()) {
    fun value(code: String): String? = cells[code]?.value
}

data class RoiTable(val template: RoiTemplate, val rows: List<RoiRowOut>)

/** The register as the 15 RoI templates, for one reference date. */
data class RoiReport(val referenceDate: LocalDate, val tables: List<RoiTable>) {
    fun table(code: String): RoiTable = tables.first { it.template.code == code }
}

/**
 * Builds the RoI templates from the register and the last scan. Pure and deterministic: rules decide every
 * value; nothing here asks the AI. A value a person or the AI entered wins over one the scan or a join gives.
 * Values still marked `ai-suggested` are left out (D7): the readiness check lists them.
 */
object RoiTables {
    const val NO_END_DATE = "9999-12-31"

    /** The taxonomy's "Link" columns: every row of these templates reports true. */
    private val LINKS = mapOf("B_02.03" to "B_02.03.0030", "B_03.01" to "B_03.01.0030", "B_03.03" to "B_03.03.0031")

    fun build(register: Register, resources: List<Resource>, referenceDate: LocalDate): RoiReport {
        val ctx = Context(register, resources, referenceDate)
        val tables = RoiSpec.templates.map { t ->
            val rows = when (t.code) {
                "B_01.01" -> listOf(ctx.entityRow())
                "B_01.02" -> listOf(ctx.entityInScopeRow()) + ctx.extraRows(t.code)
                "B_02.01" -> register.contracts.map { ctx.contractRow(it) }
                "B_02.02" -> register.contracts.flatMap { c -> c.services.flatMap { ctx.serviceRows(c, it) } }
                "B_03.01" -> register.contracts.map { ctx.signingEntityRow(it) }
                "B_03.02" -> register.contracts.map { ctx.signingProviderRow(it) }
                "B_04.01" -> ctx.usingEntityRows()
                "B_05.01" -> register.providers.map { ctx.providerRow(it) }
                "B_05.02" -> ctx.supplyChainRows()
                "B_06.01" -> ctx.functionRows()
                "B_07.01" -> ctx.assessmentRows()
                "B_99.01" -> ctx.definitionRows()
                else -> ctx.extraRows(t.code)
            }
            val link = LINKS[t.code]
            RoiTable(t, if (link == null) rows else rows.map { r -> if (link in r.cells) r else r.copy(cells = r.cells + (link to RoiCell("true", null))) })
        }
        return RoiReport(referenceDate, tables)
    }

    private class Context(val register: Register, val resources: List<Resource>, val referenceDate: LocalDate) {
        val entity = register.entity
        val providers = register.providers.associateBy { it.id }
        val resourcesById = resources.associateBy { it.id }
        val functions = register.functions.associateBy { it.id }

        // Cells

        fun field(f: RegisterField?): RoiCell? {
            val v = f?.value?.trim()
            if (v.isNullOrEmpty() || f.provenance.label == ProvenanceLabel.AiSuggested) return null
            return RoiCell(v, f.provenance.label)
        }

        fun confirmed(v: String?): RoiCell? = v?.trim()?.takeIf { it.isNotEmpty() }?.let { RoiCell(it, ProvenanceLabel.HumanConfirmed) }
        fun derived(v: String?): RoiCell? = v?.trim()?.takeIf { it.isNotEmpty() }?.let { RoiCell(it, null) }
        fun scanned(v: String?): RoiCell? = v?.trim()?.takeIf { it.isNotEmpty() }?.let { RoiCell(it, ProvenanceLabel.Scanned) }

        fun row(key: String, facts: RowFacts = RowFacts(), build: MutableMap<String, RoiCell>.() -> Unit) =
            RoiRowOut(key, buildMap { build() }, facts)

        fun MutableMap<String, RoiCell>.put(code: String, cell: RoiCell?) {
            if (cell != null) this[code] = cell
        }

        /** Copies every column of [template] an item set itself, unless a derived value is already there. */
        fun MutableMap<String, RoiCell>.own(template: String, roi: RoiFields) {
            roi.forEach { (code, f) -> if (code.startsWith("$template.")) field(f)?.let { if (code !in this) this[code] = it } }
        }

        fun date(d: LocalDate?) = d?.toString()

        // Entity

        fun entityRow() = row(entity.lei) {
            put("B_01.01.0010", confirmed(entity.lei))
            put("B_01.01.0020", confirmed(entity.name))
            put("B_01.01.0030", confirmed(entity.country))
            put("B_01.01.0060", derived(referenceDate.toString()))
            own("B_01.01", entity.roi)
        }

        fun entityInScopeRow() = row(entity.lei) {
            put("B_01.02.0010", confirmed(entity.lei))
            put("B_01.02.0020", confirmed(entity.name))
            put("B_01.02.0030", confirmed(entity.country))
            put("B_01.02.0040", field(entity.roi["B_01.02.0040"]) ?: field(entity.roi["B_01.01.0040"]))
            put("B_01.02.0070", derived(lastUpdate()))
            put("B_01.02.0090", field(entity.roi["B_01.02.0090"]) ?: derived(NO_END_DATE))
            own("B_01.02", entity.roi)
        }

        fun lastUpdate(): String? {
            val stamps = buildList {
                addAll(entity.roi.values.map { it.provenance.updatedAt })
                register.providers.forEach { p -> add(p.lei.provenance.updatedAt); addAll(p.roi.values.map { it.provenance.updatedAt }) }
                register.contracts.forEach { c ->
                    add(c.annualCost.provenance.updatedAt)
                    addAll(c.roi.values.map { it.provenance.updatedAt })
                    c.services.forEach { s -> add(s.serviceType.provenance.updatedAt); addAll(s.roi.values.map { it.provenance.updatedAt }) }
                }
                register.functions.forEach { f -> addAll(f.roi.values.map { it.provenance.updatedAt }) }
            }
            return (stamps.maxOrNull() ?: register.asOf).toLocalDateTime(TimeZone.UTC).date.toString()
        }

        fun extraRows(template: String) = register.rows[template].orEmpty().map { r ->
            row(r.id) {
                r.fields.forEach { (code, f) ->
                    val cell = field(f)
                    // Rows name providers by their register id; the RoI wants the provider's code.
                    put(code, if (cell != null && isProviderColumn(code)) providerCodeCell(cell.value) ?: cell else cell)
                }
            }
        }

        fun isProviderColumn(code: String) = code == "B_05.02.0030" || code == "B_05.02.0060"

        fun providerCodeCell(id: String): RoiCell? = providers[id]?.let { providerCode(it) }

        // Providers

        fun providerCode(p: Provider): RoiCell? = field(p.roi["B_05.01.0010"]) ?: field(p.lei)

        fun providerCodeType(p: Provider): RoiCell? =
            field(p.roi["B_05.01.0020"]) ?: if (field(p.lei) != null && p.roi["B_05.01.0010"] == null) derived("LEI") else null

        fun providerRow(p: Provider): RoiRowOut {
            val contracts = register.contracts.filter { it.providerId == p.id }
            val costs = contracts.mapNotNull { contractCost(it)?.value?.toDoubleOrNull() }
            val currency = contracts.firstNotNullOfOrNull { it.currency }
            return row(p.id, RowFacts(directProvider = contracts.isNotEmpty())) {
                put("B_05.01.0010", providerCode(p))
                put("B_05.01.0020", providerCodeType(p))
                put("B_05.01.0050", confirmed(p.name))
                if (p.name.all { it.code < 0x250 }) put("B_05.01.0060", derived(p.name))
                put("B_05.01.0080", field(p.roi["B_05.01.0080"]) ?: field(p.headquartersCountry))
                if (contracts.isNotEmpty() && costs.size == contracts.size) {
                    put("B_05.01.0100", derived(money(costs.sum())))
                    put("B_05.01.0090", derived(currency))
                }
                own("B_05.01", p.roi)
                // The provider's own code as its ultimate parent means "is the ultimate parent": reported blank.
                if (this["B_05.01.0110"]?.value == this["B_05.01.0010"]?.value) {
                    remove("B_05.01.0110"); remove("B_05.01.0120")
                }
            }
        }

        // Contracts

        fun contractCost(c: Contract): RoiCell? = field(c.annualCost) ?: scannedCost(c)

        /** Annual cost from the scan: monthly cost of the contract's resources times twelve. */
        fun scannedCost(c: Contract): RoiCell? {
            val costs = contractResources(c).mapNotNull { it.monthlyCost }
            return if (costs.isEmpty()) null else scanned(money(costs.sum() * 12))
        }

        fun contractRow(c: Contract) = row(c.id) {
            put("B_02.01.0010", confirmed(c.id))
            put("B_02.01.0040", confirmed(c.currency) ?: contractResources(c).firstNotNullOfOrNull { scanned(it.currency) })
            put("B_02.01.0050", contractCost(c))
            own("B_02.01", c.roi)
        }

        fun signingEntityRow(c: Contract) = row(c.id) {
            put("B_03.01.0010", confirmed(c.id))
            put("B_03.01.0020", field(c.roi["B_03.01.0020"]) ?: derived(entity.lei))
        }

        fun signingProviderRow(c: Contract) = row(c.id) {
            val p = providers[c.providerId]
            put("B_03.02.0010", confirmed(c.id))
            put("B_03.02.0020", p?.let { providerCode(it) })
            put("B_03.02.0030", p?.let { providerCodeType(it) })
        }

        fun usingEntityRows() = register.contracts.flatMap { c ->
            // One row per entity using the contract's services; for a single entity that is the entity itself.
            val users = c.services.map { s -> field(s.roi["B_02.02.0020"])?.value ?: entity.lei }.distinct().ifEmpty { listOf(entity.lei) }
            users.map { lei ->
                val branch = c.services.firstNotNullOfOrNull { field(it.roi["B_04.01.0040"]) }
                row("${c.id}|$lei") {
                    put("B_04.01.0010", confirmed(c.id))
                    put("B_04.01.0020", derived(lei))
                    put("B_04.01.0030", if (branch != null) derived("1") else c.services.firstNotNullOfOrNull { field(it.roi["B_04.01.0030"]) } ?: derived("2"))
                    put("B_04.01.0040", branch)
                }
            }
        }

        // Services

        fun contractResources(c: Contract): List<Resource> = c.services.flatMap { serviceResources(c, it) }.distinctBy { it.id }

        /** The service's own resources, plus resources tagged with its contract, function and type. */
        fun serviceResources(c: Contract, s: ContractService): List<Resource> {
            val listed = s.resourceIds.mapNotNull { resourcesById[it] }
            val tagged = resources.filter {
                it.contractId == c.id && it.functionId == s.functionId.value && it.serviceType?.name == s.serviceType.value
            }
            return (listed + tagged).distinctBy { it.id }
        }

        fun critical(functionId: String?): Boolean {
            val f = functions[functionId] ?: return false
            val assessed = field(f.roi["B_06.01.0050"])?.value
            return if (assessed != null) assessed == "1" else f.isCritical
        }

        /** Service column first, then the same column set on the contract (notice periods, governing law). */
        fun serviceField(c: Contract, s: ContractService, code: String): RoiCell? = field(s.roi[code]) ?: field(c.roi[code])

        fun serviceRows(c: Contract, s: ContractService): List<RoiRowOut> {
            val res = serviceResources(c, s)
            val p = providers[c.providerId]
            val start = serviceField(c, s, "B_02.02.0070") ?: confirmed(date(c.startDate))
            val end = serviceField(c, s, "B_02.02.0080") ?: (date(c.endDate)?.let(::confirmed) ?: derived(NO_END_DATE))
            val terminated = serviceField(c, s, "B_02.02.0090") != null ||
                end?.value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { it < referenceDate } == true
            val stores = serviceField(c, s, "B_02.02.0140")
                ?: if (res.isEmpty()) null else scanned((res.any { ResourceKinds.storesData(it.type) }).toString())
            val storage = countries(serviceField(c, s, "B_02.02.0150"))
                ?: legacyLocation(s)
                ?: scannedCountries(res.filter { ResourceKinds.storesData(it.type) }.flatMap { listOfNotNull(it.country) + it.replicaCountries })
            val processing = countries(serviceField(c, s, "B_02.02.0160"))
                ?: scannedCountries(res.mapNotNull { it.country })
            val provision = serviceField(c, s, "B_02.02.0130") ?: mostCommonCountry(res)
            val facts = RowFacts(
                critical = critical(s.functionId.value),
                processesData = res.isNotEmpty() || s.serviceType.value in DATA_SERVICE_TYPES,
                terminated = terminated,
            )
            val storageList = if (stores?.value == "false") emptyList() else storage.orEmpty()
            val processingList = processing.orEmpty()
            val n = maxOf(storageList.size, processingList.size, 1)
            return (0 until n).map { i ->
                row("${s.id}#${i + 1}", facts) {
                    put("B_02.02.0010", confirmed(c.id))
                    put("B_02.02.0020", field(s.roi["B_02.02.0020"]) ?: derived(entity.lei))
                    put("B_02.02.0030", p?.let { providerCode(it) })
                    put("B_02.02.0040", p?.let { providerCodeType(it) })
                    put("B_02.02.0050", field(s.functionId))
                    put("B_02.02.0060", field(s.serviceType)?.takeIf { it.value != "Unknown" })
                    put("B_02.02.0070", start)
                    put("B_02.02.0080", end)
                    put("B_02.02.0130", provision)
                    put("B_02.02.0140", stores)
                    put("B_02.02.0150", storageList.getOrNull(i) ?: storageList.firstOrNull())
                    put("B_02.02.0160", processingList.getOrNull(i) ?: processingList.firstOrNull())
                    for (code in listOf("0090", "0100", "0110", "0120", "0170", "0180")) put("B_02.02.$code", serviceField(c, s, "B_02.02.$code"))
                }
            }
        }

        /** A comma-separated country list as one cell per country, keeping the provenance. */
        fun countries(cell: RoiCell?): List<RoiCell>? =
            cell?.value?.split(',')?.map { it.trim().uppercase() }?.filter { it.isNotEmpty() }?.distinct()?.map { RoiCell(it, cell.label) }?.ifEmpty { null }

        /** The service's old single data location, kept as the storage location until someone sets B_02.02.0150. */
        fun legacyLocation(s: ContractService): List<RoiCell>? = countries(field(s.dataLocation))

        fun scannedCountries(values: List<String>): List<RoiCell>? =
            values.map { it.uppercase() }.distinct().sorted().map { RoiCell(it, ProvenanceLabel.Scanned) }.ifEmpty { null }

        fun mostCommonCountry(res: List<Resource>): RoiCell? =
            res.mapNotNull { it.country }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key?.let(::scanned)

        // Supply chain, functions, assessments, definitions

        fun supplyChainRows(): List<RoiRowOut> {
            val rankOne = register.contracts.flatMap { c ->
                val p = providers[c.providerId]
                c.services.mapNotNull { field(it.serviceType)?.value?.takeIf { v -> v != "Unknown" } }.distinct().map { type ->
                    row("${c.id}|$type|${c.providerId}") {
                        put("B_05.02.0010", confirmed(c.id))
                        put("B_05.02.0020", derived(type))
                        put("B_05.02.0030", p?.let { providerCode(it) })
                        put("B_05.02.0040", p?.let { providerCodeType(it) })
                        put("B_05.02.0050", derived("1"))
                    }
                }
            }
            val subcontractors = extraRows("B_05.02").map { r ->
                // Code types follow the providers the row names.
                val cells = r.cells.toMutableMap()
                val provider = register.rows["B_05.02"]?.firstOrNull { it.id == r.key }
                provider?.fields?.get("B_05.02.0030")?.value?.let { providers[it] }?.let { p -> providerCodeType(p)?.let { if ("B_05.02.0040" !in cells) cells["B_05.02.0040"] = it } }
                provider?.fields?.get("B_05.02.0060")?.value?.let { providers[it] }?.let { p -> providerCodeType(p)?.let { if ("B_05.02.0070" !in cells) cells["B_05.02.0070"] = it } }
                r.copy(cells = cells)
            }
            return rankOne + subcontractors
        }

        fun functionRows() = register.functions.map { f ->
            row(f.id) {
                put("B_06.01.0010", confirmed(f.id))
                put("B_06.01.0030", confirmed(f.name))
                put("B_06.01.0040", field(f.roi["B_06.01.0040"]) ?: derived(entity.lei))
                val assessment = field(f.roi["B_06.01.0050"]) ?: confirmed(if (f.isCritical) "1" else "2")
                put("B_06.01.0050", assessment)
                if (assessment?.value == "3") put("B_06.01.0070", derived(NO_END_DATE))
                own("B_06.01", f.roi)
            }
        }

        /** One row per contract and service type that supports a critical or important function. */
        fun assessmentRows(): List<RoiRowOut> = register.contracts.flatMap { c ->
            val p = providers[c.providerId]
            c.services.filter { critical(it.functionId.value) }.groupBy { it.serviceType.value }.mapNotNull { (type, services) ->
                if (type == null || type == "Unknown") return@mapNotNull null
                row("${c.id}|$type", RowFacts(critical = true)) {
                    put("B_07.01.0010", confirmed(c.id))
                    put("B_07.01.0020", p?.let { providerCode(it) })
                    put("B_07.01.0030", p?.let { providerCodeType(it) })
                    put("B_07.01.0040", derived(type))
                    for (n in listOf("0050", "0060", "0070", "0080", "0090", "0100", "0110", "0120")) {
                        val code = "B_07.01.$n"
                        put(code, services.firstNotNullOfOrNull { field(it.roi[code]) } ?: field(c.roi[code]))
                    }
                }
            }
        }

        /** The entity's own definitions, or the CIR's option labels until the admin writes them (a warning). */
        fun definitionRows(): List<RoiRowOut> {
            val own = extraRows("B_99.01")
            if (own.isNotEmpty()) return own
            return RoiSpec.definedOptionSets.flatMap { code ->
                val column = RoiSpec.column(code) ?: return@flatMap emptyList()
                column.options.map { o ->
                    row("$code|${o.key}") {
                        put("B_99.01.C0010", derived(code))
                        put("B_99.01.C0020", derived(column.name))
                        put("B_99.01.C0030", derived(o.key))
                        put("B_99.01.C0040", derived(o.label))
                    }
                }
            }
        }
    }

    /** Service types whose service handles the entity's data (Annex III), used when the scan saw no resource. */
    private val DATA_SERVICE_TYPES = setOf("S05", "S06", "S07", "S08", "S09", "S14", "S17", "S18", "S19")

    fun money(amount: Double): String {
        val cents = (amount * 100).roundToLong()
        val whole = cents / 100
        val rest = (cents % 100).toString().padStart(2, '0')
        return "$whole.$rest"
    }
}
