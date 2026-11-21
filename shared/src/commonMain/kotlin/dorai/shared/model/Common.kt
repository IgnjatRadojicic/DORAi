package dorai.shared.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** A passage from the regulation that supports an AI answer. */
@Serializable
data class Citation(
    val id: String,
    val regulation: String,
    val article: String,
    val paragraph: String? = null,
    val excerpt: String,
    val score: Double,
)

/** Where a value came from and when it last changed. */
@Serializable
data class Provenance(
    val label: ProvenanceLabel,
    val updatedAt: Instant,
    val citation: Citation? = null,
    val reason: String? = null,
)

/** A register value together with its provenance. */
@Serializable
data class RegisterField(
    val value: String? = null,
    val provenance: Provenance,
)

/** RFC 9457 problem details, the body of every error response. */
@Serializable
data class Problem(
    val type: String = "about:blank",
    val title: String,
    val status: Int,
    val detail: String? = null,
    val instance: String? = null,
)

@Serializable
data class Health(val status: String, val app: String)
