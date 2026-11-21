package dorai.app.findings

import dorai.app.components.EvidenceCitation
import dorai.app.components.FindingSeverity
import dorai.app.components.FrontendFailure
import dorai.app.validation.IssueSeverity
import dorai.app.validation.RegisterIssue
import dorai.app.validation.RegisterValidation

/** Offline examples for the standalone UI. Real findings remain the server's responsibility. */
internal object PreviewWorkspace {
    fun plan(): PlanDocument {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/preview/finpay-change.json")) { "Bundled preview plan is missing" }.use { it.readBytes() }
        return PlanDocument("finpay-change.json", bytes)
    }

    fun check(plan: PlanDocument): List<ChangeFinding> {
        if (normalize(plan.bytes) != normalize(plan().bytes)) {
            throw FrontendFailure("This plan is unavailable in demo mode.")
        }
        return listOf(
            ChangeFinding("data-location", FindingSeverity.HIGH, "Data location outside the EU / EEA",
                "aws_db_instance.payments_replica",
                "Planned data location us-east-1 is outside the EU / EEA.",
                listOf("B_02.02"), "us-east-1", "F1", "Card payment processing", true,
                EvidenceCitation("demo-location", "CIR (EU) 2024/2956", "Annex I / B_02.02", excerpt = "Demo reference. Not a regulatory quotation.")),
            ChangeFinding("critical-function-resilience", FindingSeverity.HIGH, "Critical function resilience review",
                "aws_db_instance.payments_replica",
                "The change affects critical function F1.",
                listOf("B_06.01"), "us-east-1", "F1", "Card payment processing", true),
            ChangeFinding("untagged", FindingSeverity.MEDIUM, "Business function tag missing", "aws_s3_bucket.exports",
                "The resource has no dora-function tag.", listOf("B_02.02"), "eu-central-1"),
            ChangeFinding("new-service-type", FindingSeverity.MEDIUM, "Service type requires register review", "aws_s3_bucket.exports",
                "The storage service type is not mapped to contract C2.", listOf("B_02.02"), "eu-central-1"),
        )
    }

    fun validation() = RegisterValidation(false, 2, 1, listOf(
        RegisterIssue(IssueSeverity.ERROR, "mandatory-field", "B_02.02", "Data location", "Confirm the data location for the payments replica.", "C2"),
        RegisterIssue(IssueSeverity.ERROR, "function-reference", "B_02.02", "Business function", "Assign a business function to the exports storage service.", "aws_s3_bucket.exports"),
        RegisterIssue(IssueSeverity.WARNING, "review-pending", "B_06.01", "Critical function", "Review the critical function mapping before preparing the final register.", "F1"),
    ))

    private fun normalize(bytes: ByteArray): String = buildString {
        var quoted = false
        var escaped = false
        bytes.decodeToString().removePrefix("\uFEFF").forEach { char ->
            if (quoted || !char.isWhitespace()) append(char)
            if (char == '"' && !escaped) quoted = !quoted
            escaped = quoted && char == '\\' && !escaped
        }
    }
}
