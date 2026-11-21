package dorai.app.shell

import dorai.app.components.EvidenceCitation
import dorai.app.components.FindingSeverity
import dorai.app.components.FrontendFailure
import dorai.app.components.Provenance
import dorai.app.export.ExportDocument
import dorai.app.export.ExportFormat
import dorai.app.export.ExportSource
import dorai.app.findings.ChangeFinding
import dorai.app.findings.FindingsSource
import dorai.app.validation.IssueSeverity
import dorai.app.validation.RegisterIssue
import dorai.app.validation.RegisterValidation
import dorai.app.validation.ValidationSource
import dorai.shared.api.ApiResult
import dorai.shared.api.DoraiApi
import dorai.shared.model.Citation
import dorai.shared.model.Finding
import dorai.shared.model.Severity
import dorai.shared.model.ValidationReport
import dorai.shared.model.ValidationSeverity

/**
 * Connects Matija's screens to the server through the shared client.
 * Wire values are mapped as they are: rule, severity, resource and citation never change on the way.
 */
class ServerSources(private val api: DoraiApi) {
    val findings = FindingsSource { plan ->
        api.check(plan.bytes, plan.name).orFail("The plan could not be checked.").map { it.toChangeFinding() }
    }

    val validation = ValidationSource {
        api.validation().orFail("Validation could not be loaded.").toRegisterValidation()
    }

    /** The client returns only bytes; each endpoint serves one fixed media type, so the format supplies it. */
    val export = ExportSource { format ->
        val result = when (format) {
            ExportFormat.CSV -> api.exportCsv()
            ExportFormat.PDF -> api.exportPdf()
        }
        ExportDocument(result.orFail("The export could not be downloaded."), format.mediaType)
    }
}

/** The value, or a [FrontendFailure] with a message that is safe to show (never the server's detail text). */
internal fun <T> ApiResult<T>.orFail(fallback: String): T = when (this) {
    is ApiResult.Success -> value
    is ApiResult.Failure -> throw FrontendFailure(
        when (problem.status) {
            0 -> "The server did not answer. Check the connection and retry."
            401, 403 -> "Your role does not allow this action."
            else -> "$fallback The server answered with HTTP ${problem.status}."
        }
    )
}

internal fun Finding.toChangeFinding() = ChangeFinding(
    ruleId = ruleId,
    severity = severity.toPresentation(),
    title = title,
    resourceAddress = resourceAddress,
    detail = detail,
    templates = templates,
    location = location,
    functionId = functionId,
    functionName = functionName,
    criticalFunction = criticalFunction,
    citation = citation?.toEvidence(),
    explanation = explanation,
    provenance = Provenance.PLANNED,
)

internal fun ValidationReport.toRegisterValidation() = RegisterValidation(
    isValid = isValid,
    errorCount = errorCount,
    warningCount = warningCount,
    issues = issues.map {
        RegisterIssue(
            severity = when (it.severity) {
                ValidationSeverity.Error -> IssueSeverity.ERROR
                ValidationSeverity.Warning -> IssueSeverity.WARNING
            },
            code = it.code,
            template = it.template,
            field = it.field,
            message = it.message,
            entityId = it.entityId,
        )
    },
)

private fun Severity.toPresentation() = when (this) {
    Severity.High -> FindingSeverity.HIGH
    Severity.Medium -> FindingSeverity.MEDIUM
    Severity.Low -> FindingSeverity.LOW
}

private fun Citation.toEvidence() = EvidenceCitation(id, regulation, article, paragraph, excerpt)
