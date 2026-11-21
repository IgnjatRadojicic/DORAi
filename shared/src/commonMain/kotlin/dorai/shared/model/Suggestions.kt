package dorai.shared.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** What a GLEIF lookup for one provider did: how many suggestions it added, and why (or why none). */
@Serializable
data class LeiLookupResult(val added: Int, val message: String)

/** An AI proposal a reviewer accepts or rejects. */
@Serializable
data class Suggestion(
    val id: String,
    val resourceId: String,
    val resourceName: String,
    val kind: SuggestionKind,
    val value: String,
    val reason: String,
    val citations: List<Citation>,
    val confidence: Double? = null,
    val status: SuggestionStatus,
    val createdAt: Instant,
    val decidedAt: Instant? = null,
    /**
     * For [SuggestionKind.RoiValue]: the RoI column code the value is for (for example `B_02.02.0170`).
     * [resourceId] then names the register item (service, provider, function) instead of a cloud resource.
     */
    val column: String? = null,
)
