package dorai.app.export

import dorai.app.components.FrontendFailure
import dorai.shared.api.ApiResult
import dorai.shared.api.DoraiApi

/** Uses the shared client's authentication, error handling and raw-byte export contract. */
class ApiExportSource(private val api: DoraiApi) : ExportSource {
    override suspend fun download(format: ExportFormat): ExportDocument {
        val result = when (format) {
            ExportFormat.CSV -> api.exportCsv()
            ExportFormat.PDF -> api.exportPdf()
        }
        return when (result) {
            is ApiResult.Success -> ExportDocument(result.value, format.mediaType)
            is ApiResult.Failure -> throw FrontendFailure(when (result.problem.status) {
                0 -> "Server unavailable. Check the connection."
                401 -> "Sign in to export."
                403 -> "Your role does not allow exports."
                422 -> "Resolve register validation errors before exporting."
                501 -> "Export unavailable."
                else -> "The export could not be saved. Check the connection and destination, then retry."
            })
        }
    }
}
