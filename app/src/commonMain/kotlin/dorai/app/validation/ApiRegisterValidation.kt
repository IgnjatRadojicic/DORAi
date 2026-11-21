package dorai.app.validation

import dorai.app.components.FrontendFailure
import dorai.shared.api.ApiResult
import dorai.shared.api.DoraiApi
import dorai.shared.model.ValidationSeverity

class ApiRegisterValidation(private val api: DoraiApi) : ValidationSource {
    override suspend fun validateRegister(): RegisterValidation = when (val result = api.validation()) {
        is ApiResult.Success -> result.value.let { report ->
            RegisterValidation(report.isValid, report.errorCount, report.warningCount, report.issues.map { issue ->
                RegisterIssue(if (issue.severity == ValidationSeverity.Error) IssueSeverity.ERROR else IssueSeverity.WARNING,
                    issue.code, issue.template, issue.field, issue.message, issue.entityId)
            })
        }
        is ApiResult.Failure -> throw FrontendFailure(if (result.problem.status == 0) "Server unavailable. Check the connection."
            else "Validation could not be loaded. Check the connection and retry.")
    }
}
