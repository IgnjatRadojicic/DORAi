package dorai.app.export

import dorai.app.components.FrontendFailure
import dorai.shared.api.ApiResult
import dorai.shared.api.DoraiApi

/** Uses the shared client's authentication, error handling and raw-byte export contract. */
class CsvDownloadSource(private val api: DoraiApi) : ExportSource {
    override suspend fun download(format: ExportFormat): ExportDocument {
        if (format != ExportFormat.CSV) throw FrontendFailure("Export unavailable.")
        return when (val result = api.exportCsv()) {
            is ApiResult.Success -> ExportDocument(result.value, ExportFormat.CSV.mediaType)
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
