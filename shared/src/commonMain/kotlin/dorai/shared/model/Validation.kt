package dorai.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ValidationReport(
    val isValid: Boolean,
    val errorCount: Int,
    val warningCount: Int,
    val issues: List<ValidationIssue>,
)

@Serializable
data class ValidationIssue(
    val severity: ValidationSeverity,
    val code: String,
    val template: String,
    val field: String,
    val message: String,
    val entityId: String? = null,
)
