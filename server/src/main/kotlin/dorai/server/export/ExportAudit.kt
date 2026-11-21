package dorai.server.export

import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.Register
import dorai.shared.model.RegisterField
import kotlinx.serialization.Serializable

@Serializable
internal data class CsvManifest(
    val format: String = "dorai-register-csv-draft",
    val submissionReady: Boolean = false,
    val snapshotAsOf: String,
    val source: String = "https://eur-lex.europa.eu/eli/reg_impl/2024/2956/oj/eng",
    val templates: List<TemplateSummary>,
    val omittedAiSuggestedFields: List<String>,
    val missingKnownFields: List<String>,
    val notes: List<String> = listOf(
        "This archive covers five templates from the simplified register; it is not a complete regulatory filing.",
        "Columns unsupported by the register are blank. No legal classification, authority or risk assessment is inferred.",
        "snapshotAsOf records the data snapshot, not a regulatory reporting date. An indefinite contract ends at 9999-12-31 in the CSV.",
        "Provider references use available LEIs. Unconfirmed AI values are excluded from every table and derived total.",
        "Provider totals require every amount and a single currency. No exchange rate or partial sum is used.",
        "Only ASCII provider names are repeated as Latin names. Other spellings require explicit register data.",
        "Text that could be interpreted as a spreadsheet formula has an apostrophe prefix.",
    ),
)

@Serializable
internal data class TemplateSummary(val file: String, val columns: Int, val rows: Int, val unavailableColumns: List<String>)

internal fun manifest(register: Register): CsvManifest {
    val omitted = mutableListOf<String>()
    val missing = mutableListOf<String>()
    fun audit(field: RegisterField, fieldPath: String, cellPath: String, isMissing: Boolean = ExportCells.text(field).isBlank()) {
        if (field.provenance.label == ProvenanceLabel.AiSuggested) omitted += fieldPath
        if (isMissing) missing += cellPath
    }
    register.providers.sortedBy { it.id }.forEach { provider ->
        val path = "B_05.01/${provider.id}"
        audit(provider.lei, "$path/Lei", "$path/B_05.01.0010")
        audit(provider.headquartersCountry, "$path/HeadquartersCountry", "$path/B_05.01.0080")
        if (provider.name.any { it.code >= 128 }) missing += "$path/B_05.01.0060"
        if (ExportCells.providerTotal(provider.id, register).second == null) missing += "$path/B_05.01.0100"
    }
    register.contracts.sortedBy { it.id }.forEach { contract ->
        audit(contract.annualCost, "B_02.01/${contract.id}/AnnualCost", "B_02.01/${contract.id}/B_02.01.0050", ExportCells.amount(contract.annualCost) == null)
        contract.services.sortedBy { it.id }.forEach { service ->
            val path = "B_02.02/${service.id}"
            audit(service.serviceType, "$path/ServiceType", "$path/B_02.02.0060", ExportCells.text(service.serviceType).let { it.isBlank() || it == "Unknown" })
            audit(service.functionId, "$path/FunctionId", "$path/B_02.02.0050")
            audit(service.dataLocation, "$path/DataLocation", "$path/B_02.02.0150")
        }
    }
    return CsvManifest(
        snapshotAsOf = register.asOf.toString(),
        templates = CsvTemplate.entries.map { template ->
            TemplateSummary(template.fileName, template.columns.size, template.rowCount(register), template.unavailable.map(template::column))
        },
        omittedAiSuggestedFields = omitted.sorted(), missingKnownFields = missing.sorted(),
    )
}
