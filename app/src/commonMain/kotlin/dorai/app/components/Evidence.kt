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
class FrontendFailure(message: String) : Exception(message)

fun Throwable.displayMessage(fallback: String): String =
    if (this is FrontendFailure) message?.takeIf { it.isNotBlank() } ?: fallback else fallback
