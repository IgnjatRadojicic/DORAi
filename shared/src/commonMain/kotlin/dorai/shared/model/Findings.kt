package dorai.shared.model

import kotlinx.serialization.Serializable

/** A problem found by a rule. Only rules create findings; the AI may add an explanation and citation. */
@Serializable
data class Finding(
    val ruleId: String,
    val severity: Severity,
    val title: String,
    val resourceAddress: String,
    val templates: List<String> = emptyList(),
    val detail: String,
    val location: String? = null,
    val functionId: String? = null,
    val functionName: String? = null,
    val criticalFunction: Boolean = false,
    val citation: Citation? = null,
    val explanation: String? = null,
)
