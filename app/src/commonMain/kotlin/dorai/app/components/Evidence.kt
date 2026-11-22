package dorai.app.components

enum class FindingSeverity { HIGH, MEDIUM, LOW }

enum class Provenance(val label: String) {
    SCANNED("scanned"), PLANNED("planned"), AI_SUGGESTED("ai-suggested"), HUMAN_CONFIRMED("human-confirmed")
}

/** Presentation model only. The shared API owns serialization and wire enum values. */
data class EvidenceCitation(
    val id: String,
    val regulation: String,
    val article: String,
    val paragraph: String? = null,
    val excerpt: String,
)

/** Only messages deliberately intended for display should be passed to this exception. */
/** A problem with a message safe to show. [retryable] is false when trying the same thing again can't help (the server refused the input). */
class FrontendFailure(message: String, val retryable: Boolean = true) : Exception(message)

/** False only for a [FrontendFailure] that says retrying can't help. */
val Throwable.retryable: Boolean get() = (this as? FrontendFailure)?.retryable ?: true

fun Throwable.displayMessage(fallback: String): String =
    if (this is FrontendFailure) message?.takeIf { it.isNotBlank() } ?: fallback else fallback
