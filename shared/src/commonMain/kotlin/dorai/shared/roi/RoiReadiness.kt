package dorai.shared.roi

import dorai.shared.model.Register
import dorai.shared.model.RoiFields
import dorai.shared.model.ValidationIssue
import dorai.shared.model.ValidationSeverity
import dorai.shared.rules.Lei
import dorai.shared.rules.Regions
import kotlinx.datetime.LocalDate

/**
 * What stops the register from being exported as a complete RoI, cell by cell. Deterministic: the CIR's
 * fill-in conditions, formats, and references between templates. Errors block the final package; the
 * export can still be built as a marked draft (D7).
 */
object RoiReadiness {

    fun check(report: RoiReport, register: Register): List<ValidationIssue> = buildList {
        report.tables.forEach { table ->
            table.rows.forEach { row ->
                table.template.columns.forEach { col -> checkCell(col, row)?.let(::add) }
            }
        }
        addAll(references(report))
        addAll(codeRules(report))
        addAll(uniqueness(report))
        addAll(unconfirmed(register))
        if (!TaxonomyCodes.verified) {
            add(warning("taxonomy-unverified", "", "", "Package layout and closed-list codes are not yet checked against the EBA DORA taxonomy release ${TaxonomyCodes.frameworkRelease}.", null))
        }
        if (register.rows["B_99.01"].isNullOrEmpty()) {
            add(warning("default-definitions", "B_99.01", "C0040", "Definitions are the CIR option labels. Replace them with the entity's own definitions.", null))
        }
    }

    /** True when the CIR asks for this column in this row. */
    fun required(col: RoiColumn, row: RoiRowOut): Boolean = when (col.need) {
        RoiNeed.Mandatory, RoiNeed.IfReporting -> true
        RoiNeed.Optional -> false
        RoiNeed.IfTotalAssets -> row.value(col.template + ".0110") != null
        RoiNeed.IfFinancialEntity -> row.value(col.template + ".0040") !in setOf("23", "24")
        RoiNeed.IfSubsequent -> row.value("B_02.01.0020") == "3"
        RoiNeed.IfTerminated -> row.facts.terminated
        RoiNeed.IfCritical -> row.facts.critical
        RoiNeed.IfStoresData -> row.value("B_02.02.0140") == "true"
        RoiNeed.IfProcessesData -> row.facts.processesData
        RoiNeed.IfStoresAndCritical -> row.facts.critical && row.value("B_02.02.0140") == "true"
        RoiNeed.IfBranch -> row.value("B_04.01.0030") == "1"
        RoiNeed.IfAdditionalCode -> row.value("B_05.01.0030") != null
        RoiNeed.IfAmountReported -> row.value("B_05.01.0100") != null
        RoiNeed.IfDirectProvider -> row.facts.directProvider
        // Blank is how the provider says it is its own ultimate parent: set it to its own code, export leaves it out.
        RoiNeed.IfNotUltimateParent -> false
        RoiNeed.IfRankAboveOne -> (row.value("B_05.02.0050")?.toIntOrNull() ?: 1) > 1
        RoiNeed.IfHardToSubstitute -> row.value("B_07.01.0050") in setOf("1", "2")
    }

    private fun checkCell(col: RoiColumn, row: RoiRowOut): ValidationIssue? {
        val value = row.value(col.code)
        if (value == null) {
            return if (required(col, row)) {
                error("roi-missing", col, "${col.name} is missing (${col.need.description}).", row.key)
            } else null
        }
        val ok = when (col.type) {
            RoiType.Text -> value.isNotBlank()
            RoiType.Lei -> Lei.isValid(value) || EUID.matches(value)
            RoiType.Country -> value.length == 2 && value.all { it in 'A'..'Z' }
            RoiType.Currency -> value.length == 3 && value.all { it in 'A'..'Z' }
            RoiType.Date -> runCatching { LocalDate.parse(value) }.isSuccess
            RoiType.Monetary -> MONEY.matches(value)
            RoiType.NaturalNumber -> value.all { it.isDigit() }
            RoiType.YesNo, RoiType.Choice -> col.options.any { it.key == value }
            RoiType.CodeType -> CODE_TYPE.matches(value)
            RoiType.FunctionId -> FUNCTION_ID.matches(value)
        }
        return if (ok) null else error("roi-format", col, "'$value' is not a valid ${col.type.describe()} for ${col.name}.", row.key)
    }

    /** "As reported in": every reference names a row that exists in the referenced template. */
    private fun references(report: RoiReport): List<ValidationIssue> = buildList {
        val contracts = report.table("B_02.01").rows.mapNotNull { it.value("B_02.01.0010") }.toSet()
        val providers = report.table("B_05.01").rows.mapNotNull { it.value("B_05.01.0010") }.toSet()
        val functions = report.table("B_06.01").rows.mapNotNull { it.value("B_06.01.0010") }.toSet()
        val entities = report.table("B_01.02").rows.mapNotNull { it.value("B_01.02.0010") }.toSet()
        val branches = report.table("B_01.03").rows.mapNotNull { it.value("B_01.03.0010") }.toSet()

        fun ref(template: String, column: String, known: Set<String>, what: String) =
            report.table(template).rows.forEach { row ->
                val v = row.value("$template.$column") ?: return@forEach
                if (v !in known) add(error("roi-unknown-reference", RoiSpec.column("$template.$column")!!, "$what '$v' is not reported in the register.", row.key))
            }

        for (t in listOf("B_02.02", "B_03.01", "B_03.02", "B_04.01", "B_05.02", "B_07.01")) ref(t, "0010", contracts, "Contractual arrangement")
        ref("B_02.03", "0010", contracts, "Contractual arrangement")
        ref("B_02.03", "0020", contracts, "Contractual arrangement")
        ref("B_03.03", "0010", contracts, "Contractual arrangement")
        for ((t, c) in listOf("B_02.02" to "0030", "B_03.02" to "0020", "B_05.02" to "0030", "B_05.02" to "0060", "B_07.01" to "0020")) ref(t, c, providers, "Provider")
        ref("B_02.02", "0050", functions, "Function")
        ref("B_01.03", "0020", entities, "Entity")
        ref("B_03.03", "0020", entities, "Entity")
        ref("B_04.01", "0040", branches, "Branch")

        // A subcontractor's recipient sits one rank up in the same chain.
        val chain = report.table("B_05.02").rows
        chain.forEach { row ->
            val rank = row.value("B_05.02.0050")?.toIntOrNull() ?: return@forEach
            val recipient = row.value("B_05.02.0060") ?: return@forEach
            if (rank < 2) return@forEach
            val found = chain.any {
                it.value("B_05.02.0010") == row.value("B_05.02.0010") && it.value("B_05.02.0020") == row.value("B_05.02.0020") &&
                    it.value("B_05.02.0030") == recipient && it.value("B_05.02.0050")?.toIntOrNull() == rank - 1
            }
            if (!found) add(error("roi-broken-chain", RoiSpec.column("B_05.02.0060")!!, "Recipient '$recipient' is not at rank ${rank - 1} of this supply chain.", row.key))
        }
    }

    /** B_05.01.0020: only LEI or EUID for legal persons, only LEI for legal persons outside the Union. */
    private fun codeRules(report: RoiReport): List<ValidationIssue> = buildList {
        val col = RoiSpec.column("B_05.01.0020")!!
        report.table("B_05.01").rows.forEach { row ->
            val type = row.value("B_05.01.0020") ?: return@forEach
            if (row.value("B_05.01.0070") != "1") return@forEach
            val country = row.value("B_05.01.0080")
            when {
                type != "LEI" && type != "EUID" ->
                    add(error("roi-legal-person-code", col, "A legal person must be identified by LEI or EUID, not $type.", row.key))
                type == "EUID" && country != null && country !in EU ->
                    add(error("roi-legal-person-code", col, "A legal person established outside the Union must be identified by LEI.", row.key))
            }
        }
    }

    /** B_06.01.0010: one identifier per entity, function name and licensed activity. */
    private fun uniqueness(report: RoiReport): List<ValidationIssue> = buildList {
        val col = RoiSpec.column("B_06.01.0010")!!
        report.table("B_06.01").rows
            .groupBy { Triple(it.value("B_06.01.0040"), it.value("B_06.01.0030"), it.value("B_06.01.0020")) }
            .filter { (key, rows) -> key.third != null && rows.size > 1 }
            .forEach { (_, rows) ->
                add(error("roi-duplicate-function", col, "Functions ${rows.joinToString { it.key }} have the same name and licensed activity.", rows.first().key))
            }
    }

    /** Values still waiting for a person to accept or reject them (product rule: a person decides). */
    private fun unconfirmed(register: Register): List<ValidationIssue> = buildList {
        fun scan(id: String, roi: RoiFields) = roi.forEach { (code, f) ->
            if (f.provenance.label == dorai.shared.model.ProvenanceLabel.AiSuggested && !f.value.isNullOrBlank()) {
                val col = RoiSpec.column(code) ?: return@forEach
                add(warning("roi-unconfirmed", col.template, col.code, "${col.name} is an AI suggestion nobody has accepted yet; it is left out of the export.", id))
            }
        }
        scan(register.entity.lei, register.entity.roi)
        register.providers.forEach { scan(it.id, it.roi) }
        register.functions.forEach { scan(it.id, it.roi) }
        register.contracts.forEach { c -> scan(c.id, c.roi); c.services.forEach { scan(it.id, it.roi) } }
    }

    private fun error(code: String, col: RoiColumn, message: String, id: String?) =
        ValidationIssue(ValidationSeverity.Error, code, col.template, col.code, message, id)

    private fun warning(code: String, template: String, field: String, message: String, id: String?) =
        ValidationIssue(ValidationSeverity.Warning, code, template, field, message, id)

    private fun RoiType.describe() = when (this) {
        RoiType.Lei -> "LEI or EUID"
        RoiType.Country -> "ISO 3166-1 alpha-2 country code"
        RoiType.Currency -> "ISO 4217 currency code"
        RoiType.Date -> "date (yyyy-mm-dd)"
        RoiType.Monetary -> "amount"
        RoiType.NaturalNumber -> "whole number"
        RoiType.YesNo -> "yes or no"
        RoiType.Choice -> "option"
        RoiType.CodeType -> "code type (LEI, EUID or country_CRN/VAT/PNR/NIN)"
        RoiType.FunctionId -> "function identifier (F followed by a number)"
        RoiType.Text -> "text"
    }

    private val MONEY = Regex("""-?\d+(\.\d+)?""")
    private val CODE_TYPE = Regex("""LEI|EUID|[A-Z]{2}_(CRN|VAT|PNR|NIN)""")
    private val FUNCTION_ID = Regex("""F[1-9]\d*""")
    private val EUID = Regex("""[A-Z]{2}[A-Z0-9.]{3,}""")
    private val EU = Regions.euEeaCountries - setOf("IS", "LI", "NO")
}
