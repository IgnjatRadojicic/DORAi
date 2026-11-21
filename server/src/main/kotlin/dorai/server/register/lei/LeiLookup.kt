package dorai.server.register.lei

import dorai.shared.model.Citation
import dorai.shared.model.LeiLookupResult
import dorai.shared.model.Provider
import dorai.shared.model.Suggestion
import dorai.shared.model.SuggestionKind
import dorai.shared.model.SuggestionStatus
import dorai.shared.roi.RoiSpec
import dorai.shared.roi.VendorChecks
import java.util.UUID
import kotlin.time.Instant

/**
 * Fills a provider's identity columns (B_05.01) from the public LEI register. The match is deterministic:
 * the provider's own LEI, or exactly one active record whose legal name equals the provider's name. Every
 * value becomes a suggestion a reviewer accepts or rejects; nothing is written directly.
 */
class LeiLookup(private val directory: LeiDirectory) {

    data class Outcome(val suggestions: List<Suggestion>, val result: LeiLookupResult)

    suspend fun lookup(provider: Provider, now: Instant): Outcome {
        val known = provider.roi["B_05.01.0010"]?.value ?: provider.lei.value
        val byLei = known?.takeIf { LEI.matches(it) }?.let { directory.record(it) }
        val record = byLei ?: run {
            val name = VendorChecks.normalize(provider.name)
            val hits = directory.search(provider.name).filter { VendorChecks.normalize(it.legalName) == name && it.status.equals("ACTIVE", true) }
            hits.singleOrNull() ?: return none(
                if (hits.isEmpty()) "No active LEI record has the legal name ${provider.name}: insufficient evidence, enter the LEI by hand."
                else "${hits.size} LEI records share the name ${provider.name}: insufficient evidence, enter the LEI by hand.",
            )
        }
        val parent = directory.ultimateParent(record.lei)?.takeIf { it != record.lei }

        val values = buildMap {
            put("B_05.01.0010", record.lei)
            put("B_05.01.0020", "LEI")
            put("B_05.01.0050", record.legalName)
            record.headquartersCountry?.let { put("B_05.01.0080", it) }
            if (parent != null) {
                put("B_05.01.0110", parent)
                put("B_05.01.0120", "LEI")
            }
        }
        val current = mapOf("B_05.01.0010" to known, "B_05.01.0080" to provider.headquartersCountry.value) +
            provider.roi.mapValues { it.value.value }
        val how = if (byLei != null) "the provider's LEI" else "its legal name"
        val reason = "GLEIF record ${record.lei} (${record.status}), found by $how: ${record.legalName}" +
            (record.headquartersCountry?.let { ", headquarters $it" } ?: "") +
            (parent?.let { ", ultimate parent $it" } ?: ", no ultimate parent reported") + "."
        val suggestions = values.filter { (column, value) -> current[column] != value }.map { (column, value) ->
            Suggestion(
                id = UUID.randomUUID().toString(),
                resourceId = provider.id,
                resourceName = provider.name,
                kind = SuggestionKind.RoiValue,
                value = value,
                reason = reason,
                citations = listOf(citation(column)),
                confidence = if (byLei != null) 1.0 else 0.9,
                status = SuggestionStatus.Pending,
                createdAt = now,
                column = column,
            )
        }
        val message = if (suggestions.isEmpty()) "The register already matches GLEIF record ${record.lei}."
        else "${suggestions.size} values from GLEIF record ${record.lei} are waiting for review."
        return Outcome(suggestions, LeiLookupResult(suggestions.size, message))
    }

    private fun none(message: String) = Outcome(emptyList(), LeiLookupResult(0, message))

    /** The column instruction in the CIR that asks for the value. */
    private fun citation(column: String) = Citation(
        id = "cir-2024-2956-$column",
        regulation = "Commission Implementing Regulation (EU) 2024/2956",
        article = "Annex I",
        paragraph = column,
        excerpt = RoiSpec.column(column)?.name.orEmpty(),
        score = 1.0,
    )

    private companion object {
        val LEI = Regex("[A-Z0-9]{18}[0-9]{2}")
    }
}
