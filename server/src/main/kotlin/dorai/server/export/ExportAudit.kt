package dorai.server.export

import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.Register
import dorai.shared.model.RegisterField

/** Register fields the PDF lists as awaiting a person: unaccepted AI proposals and known gaps. */
internal data class ReviewAudit(val omittedAiSuggestedFields: List<String>, val missingKnownFields: List<String>)

internal fun manifest(register: Register): ReviewAudit {
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
    return ReviewAudit(omittedAiSuggestedFields = omitted.sorted(), missingKnownFields = missing.sorted())
}
