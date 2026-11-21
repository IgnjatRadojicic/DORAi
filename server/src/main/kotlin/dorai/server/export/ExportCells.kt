package dorai.server.export

import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.Register
import dorai.shared.model.RegisterField
import java.math.BigDecimal

internal object ExportCells {
    fun text(field: RegisterField): String =
        if (field.provenance.label == ProvenanceLabel.AiSuggested) "" else field.value.orEmpty()

    fun amount(field: RegisterField): BigDecimal? = text(field).trim().takeIf { it.isNotEmpty() }
        ?.toBigDecimalOrNull()

    fun providerTotal(providerId: String, register: Register): Pair<String?, BigDecimal?> {
        val contracts = register.contracts.filter { it.providerId == providerId }
        if (contracts.isEmpty() || contracts.any { amount(it.annualCost) == null || it.currency.isNullOrBlank() }) return null to null
        val currencies = contracts.map { it.currency }.distinct()
        if (currencies.size != 1) return null to null
        return currencies.single() to contracts.fold(BigDecimal.ZERO) { total, contract -> total + checkNotNull(amount(contract.annualCost)) }
    }

}
