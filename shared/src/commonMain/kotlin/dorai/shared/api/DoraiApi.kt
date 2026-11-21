package dorai.shared.api

import dorai.shared.Dorai
import dorai.shared.model.ConcentrationReport
import dorai.shared.model.DoraiJson
import dorai.shared.model.Finding
import dorai.shared.model.Health
import dorai.shared.model.Problem
import dorai.shared.model.Register
import dorai.shared.model.Resource
import dorai.shared.model.ScanResult
import dorai.shared.model.Suggestion
import dorai.shared.model.ValidationReport
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException

/**
 * Client for the DORAi server, used by the apps and the CLI.
 * Every call returns [ApiResult]; it never throws for HTTP or network errors.
 *
 * @param baseUrl server address without `/api`, for example `http://localhost:8080`.
 * @param httpClient engine to use; the default picks OkHttp on desktop and Android. Tests pass their own.
 */
class DoraiApi(baseUrl: String, httpClient: HttpClient = HttpClient()) : AutoCloseable {

    private val http = httpClient.config {
        expectSuccess = false
        install(ContentNegotiation) { json(DoraiJson) }
        defaultRequest { url(baseUrl.trimEnd('/') + Dorai.API_PREFIX + "/") }
    }

    suspend fun health(): ApiResult<Health> = call { http.get("health") }

    // Scan and inventory
    suspend fun scan(): ApiResult<ScanResult> = call { http.post("scan") }
    suspend fun resources(): ApiResult<List<Resource>> = call { http.get("resources") }

    // Register
    suspend fun register(): ApiResult<Register> = call { http.get("register") }
    suspend fun validation(): ApiResult<ValidationReport> = call { http.get("validation") }
    suspend fun concentration(): ApiResult<ConcentrationReport> = call { http.get("concentration") }

    // AI suggestions, decided by a reviewer
    suspend fun suggestions(): ApiResult<List<Suggestion>> = call { http.get("suggestions") }

    /** Asks the AI for new suggestions, for one resource or (when null) for all. */
    suspend fun generateSuggestions(resourceId: String? = null): ApiResult<List<Suggestion>> =
        call { http.post("suggestions/generate") { resourceId?.let { parameter("resourceId", it) } } }

    suspend fun acceptSuggestion(id: String): ApiResult<Suggestion> = call { http.post("suggestions/$id/accept") }
    suspend fun rejectSuggestion(id: String): ApiResult<Suggestion> = call { http.post("suggestions/$id/reject") }

    /**
     * Runs the rules on a Terraform plan (`terraform show -json` output).
     * Without [planJson] the server checks its built-in demo plan.
     */
    suspend fun check(planJson: ByteArray? = null, fileName: String = "plan.json"): ApiResult<List<Finding>> =
        call {
            http.submitFormWithBinaryData(
                url = "check",
                formData = formData {
                    if (planJson != null) {
                        append(
                            "plan",
                            planJson,
                            Headers.build {
                                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            },
                        )
                    }
                },
            )
        }

    // Export: raw file bytes (zip of EBA CSV files, PDF report)
    suspend fun exportCsv(): ApiResult<ByteArray> = call { http.get("export/csv") }
    suspend fun exportPdf(): ApiResult<ByteArray> = call { http.get("export/pdf") }

    override fun close() = http.close()

    private suspend inline fun <reified T> call(request: () -> HttpResponse): ApiResult<T> =
        try {
            val response = request()
            if (response.status.isSuccess()) ApiResult.Success(response.body<T>())
            else ApiResult.Failure(response.toProblem())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Failure(Problem(title = "Network error", status = 0, detail = e.message))
        }

    private suspend fun HttpResponse.toProblem(): Problem {
        val text = bodyAsText()
        return runCatching { DoraiJson.decodeFromString<Problem>(text) }
            .getOrElse { Problem(title = status.description, status = status.value, detail = text.ifBlank { null }) }
    }
}
