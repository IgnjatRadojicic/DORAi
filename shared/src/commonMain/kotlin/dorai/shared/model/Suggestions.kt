package dorai.shared.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

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
)
