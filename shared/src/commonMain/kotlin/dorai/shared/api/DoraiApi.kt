package dorai.shared.api

import dorai.shared.model.RoiValueEdit
import dorai.shared.model.LeiLookupResult
import kotlinx.datetime.LocalDate
import io.ktor.http.encodeURLPathPart
import io.ktor.client.request.delete
import io.ktor.client.request.put
import dorai.shared.roi.RoiView
import dorai.shared.model.RoiRow
import dorai.shared.model.Provider
import dorai.shared.model.FinancialEntity
import dorai.shared.model.Contract
import dorai.shared.model.BusinessFunction
import dorai.shared.Dorai
import dorai.shared.Version
import kotlin.concurrent.Volatile
import dorai.shared.model.CreateInviteRequest
import dorai.shared.model.CreateUserRequest
import dorai.shared.model.CreatedUser
import dorai.shared.model.Invite
import dorai.shared.model.LoginRequest
import dorai.shared.model.LoginResponse
import dorai.shared.model.PasswordChangeRequest
import dorai.shared.model.RedeemRequest
import dorai.shared.model.RedeemResponse
import dorai.shared.model.RefreshRequest
import dorai.shared.model.Tokens
import dorai.shared.model.User
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import io.ktor.http.ContentDisposition
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
 * With a [tokens] store, every request carries the access token. When the server answers 401, the client
 * refreshes once (the refresh token is single use, so refreshes are serialized) and repeats the request.
 * If the refresh fails, the store is cleared and the caller sees the 401: the user must log in again.
 *
 * @param baseUrl server address without `/api`, for example `https://dorai.firm.local:8443`.
 * @param httpClient engine to use; by default OkHttp on desktop and Android, pinned when [fingerprint] is set. Tests pass their own.
 * @param tokens where the app keeps the tokens; null for a server without auth (development, fixtures).
 * @param fingerprint SHA-256 of the server certificate from the invite link: only that certificate is
 *   trusted, and a different one fails with [CertificateProblem.CHANGED]. Null for a plain `http` server.
 */
class DoraiApi(
    baseUrl: String,
    httpClient: HttpClient? = null,
    private val tokens: TokenStore? = null,
    fingerprint: String? = null,
) : AutoCloseable {

    private val refreshLock = Mutex()

    /**
     * The server's version from the last response, null before the first one. Every request says this
     * client's version; a server with another major answers with a [dorai.shared.VersionProblem] type.
     */
    @Volatile
    var serverVersion: Version? = null
        private set

    private val http = (httpClient ?: fingerprint?.let(::pinnedHttpClient) ?: HttpClient()).config {
        expectSuccess = false
        install(ContentNegotiation) { json(DoraiJson) }
        defaultRequest {
            url(baseUrl.trimEnd('/') + Dorai.API_PREFIX + "/")
            // Once, even when the caller's client already sets it.
            if (!headers.contains(Version.CLIENT_HEADER)) headers.append(Version.CLIENT_HEADER, Version.CURRENT.toString())
        }
        install(
            createClientPlugin("DoraiServerVersion") {
                onResponse { response ->
                    response.headers[Version.SERVER_HEADER]?.let(Version::parse)?.let { serverVersion = it }
                }
            },
        )
        install(
            createClientPlugin("DoraiBearer") {
                onRequest { request, _ ->
                    if (request.headers[HttpHeaders.Authorization] == null) {
                        tokens?.load()?.let { request.bearerAuth(it.accessToken) }
                    }
                }
            },
        )
    }

    suspend fun health(): ApiResult<Health> = call { http.get("health") }

    // Connecting and logging in. Successful logins, password changes and refreshes are saved to [tokens].

    /** Tells which user an invite code is for. Does not log in. */
    suspend fun redeem(code: String): ApiResult<RedeemResponse> =
        call { http.post("auth/redeem") { json(RedeemRequest(code)) } }

    suspend fun login(username: String, password: String): ApiResult<LoginResponse> =
        call<LoginResponse> { http.post("auth/login") { json(LoginRequest(username, password)) } }
            .also { r -> if (r is ApiResult.Success) r.value.tokens?.let { tokens?.save(it) } }

    suspend fun changePassword(username: String, currentPassword: String, newPassword: String): ApiResult<Tokens> =
        call<Tokens> { http.post("auth/password") { json(PasswordChangeRequest(username, currentPassword, newPassword)) } }
            .also { r -> if (r is ApiResult.Success) tokens?.save(r.value) }

    /** Revokes the refresh token on the server and forgets the tokens here. */
    suspend fun logout(): ApiResult<Unit> {
        val store = tokens ?: return ApiResult.Success(Unit)
        val current = store.load() ?: return ApiResult.Success(Unit)
        val result = call<Unit> { http.post("auth/logout") { json(RefreshRequest(current.refreshToken)) } }
        store.save(null)
        return result
    }

    // Administration (admin role)
    suspend fun users(): ApiResult<List<User>> = call { http.get("admin/users") }
    suspend fun createUser(request: CreateUserRequest): ApiResult<CreatedUser> = call { http.post("admin/users") { json(request) } }
    suspend fun disableUser(username: String): ApiResult<User> = call { http.post("admin/users/$username/disable") }
    /** Lets a disabled user sign in again with their password. */
    suspend fun enableUser(username: String): ApiResult<User> = call { http.post("admin/users/$username/enable") }
    /** Signs the user out on every device at once; they can log in again. */
    suspend fun revokeSessions(username: String): ApiResult<Unit> = call { http.post("admin/users/$username/revoke") }
    suspend fun invites(): ApiResult<List<Invite>> = call { http.get("admin/invites") }
    suspend fun createInvite(username: String): ApiResult<Invite> = call { http.post("admin/invites") { json(CreateInviteRequest(username)) } }

    // Scan and inventory
    suspend fun scan(): ApiResult<ScanResult> = call { http.post("scan") }
    suspend fun resources(): ApiResult<List<Resource>> = call { http.get("resources") }

    // Register
    suspend fun register(): ApiResult<Register> = call { http.get("register") }
    suspend fun validation(): ApiResult<ValidationReport> = call { http.get("validation") }
    suspend fun concentration(): ApiResult<ConcentrationReport> = call { http.get("concentration") }

    /** The RoI templates and what is missing before export, for a reference date (default: today). */
    suspend fun roi(referenceDate: LocalDate? = null): ApiResult<RoiView> =
        call { http.get("roi") { referenceDate?.let { parameter("date", it.toString()) } } }

    // Register edits. The server sets provenance: a changed value becomes human-confirmed by the caller.
    suspend fun putEntity(entity: FinancialEntity): ApiResult<FinancialEntity> = call { http.put("register/entity") { json(entity) } }
    suspend fun putProvider(provider: Provider): ApiResult<Provider> = call { http.put("register/providers/${provider.id.encodeURLPathPart()}") { json(provider) } }
    suspend fun deleteProvider(id: String): ApiResult<Unit> = call { http.delete("register/providers/${id.encodeURLPathPart()}") }

    /** One RoI cell of a register item (provider, contract, service, function); null clears it. */
    suspend fun putValue(itemId: String, column: String, value: String?): ApiResult<Unit> =
        call { http.put("register/values/${itemId.encodeURLPathPart()}/${column.encodeURLPathPart()}") { json(RoiValueEdit(value)) } }

    /** One RoI cell of the entity itself (admin). */
    suspend fun putEntityValue(column: String, value: String?): ApiResult<Unit> =
        call { http.put("register/entity/values/${column.encodeURLPathPart()}") { json(RoiValueEdit(value)) } }

    /** Looks the provider up in GLEIF; the values arrive as suggestions to review. */
    suspend fun leiLookup(providerId: String): ApiResult<LeiLookupResult> = call { http.post("register/providers/${providerId.encodeURLPathPart()}/lei-lookup") }
    suspend fun putContract(contract: Contract): ApiResult<Contract> = call { http.put("register/contracts/${contract.id.encodeURLPathPart()}") { json(contract) } }
    suspend fun deleteContract(id: String): ApiResult<Unit> = call { http.delete("register/contracts/${id.encodeURLPathPart()}") }
    suspend fun putFunction(function: BusinessFunction): ApiResult<BusinessFunction> = call { http.put("register/functions/${function.id.encodeURLPathPart()}") { json(function) } }
    suspend fun deleteFunction(id: String): ApiResult<Unit> = call { http.delete("register/functions/${id.encodeURLPathPart()}") }
    suspend fun putRow(template: String, row: RoiRow): ApiResult<RoiRow> = call { http.put("register/rows/$template/${row.id.encodeURLPathPart()}") { json(row) } }
    suspend fun deleteRow(template: String, id: String): ApiResult<Unit> = call { http.delete("register/rows/$template/${id.encodeURLPathPart()}") }

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
    /**
     * The RoI xBRL-CSV package. The server refuses the final package (422) while readiness errors remain;
     * [draft] builds it anyway, named as a draft.
     */
    suspend fun exportCsv(referenceDate: LocalDate? = null, draft: Boolean = false): ApiResult<ByteArray> = call {
        http.get("export/csv") {
            referenceDate?.let { parameter("date", it.toString()) }
            if (draft) parameter("draft", "true")
        }
    }

    /** As [exportCsv], with the package name the server gives (EBA filing name, `DRAFT_` in front of a draft). */
    suspend fun exportCsvFile(referenceDate: LocalDate? = null, draft: Boolean = false): ApiResult<DownloadedFile> = call {
        http.get("export/csv") {
            referenceDate?.let { parameter("date", it.toString()) }
            if (draft) parameter("draft", "true")
        }
    }

    suspend fun exportPdf(referenceDate: LocalDate? = null): ApiResult<ByteArray> =
        call { http.get("export/pdf") { referenceDate?.let { parameter("date", it.toString()) } } }

    override fun close() = http.close()

    private suspend inline fun <reified T> call(request: () -> HttpResponse): ApiResult<T> =
        try {
            val sentWith = tokens?.load()?.accessToken
            var response = request()
            if (response.status == HttpStatusCode.Unauthorized && sentWith != null && refresh(sentWith)) {
                response = request()
            }
            when {
                !response.status.isSuccess() -> ApiResult.Failure(response.toProblem())
                T::class == Unit::class -> ApiResult.Success(Unit as T)
                T::class == DownloadedFile::class -> ApiResult.Success(DownloadedFile(response.attachmentName(), response.body<ByteArray>()) as T)
                else -> ApiResult.Success(response.body<T>())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val changed = generateSequence<Throwable>(e) { it.cause }.filterIsInstance<CertificateChanged>().firstOrNull()
            if (changed != null) {
                ApiResult.Failure(Problem(type = CertificateProblem.CHANGED, title = "Server certificate changed", status = 0, detail = changed.message))
            } else {
                ApiResult.Failure(Problem(title = "Network error", status = 0, detail = e.message))
            }
        }

    /**
     * Swaps the refresh token for new tokens. Returns whether a retry makes sense. Another request may have
     * refreshed while this one waited for the lock; then its new token is used instead of refreshing again,
     * since reusing a spent refresh token makes the server revoke the whole session.
     */
    private suspend fun refresh(rejectedAccessToken: String): Boolean = refreshLock.withLock {
        val store = tokens ?: return false
        val current = store.load() ?: return false
        if (current.accessToken != rejectedAccessToken) return true
        val response = http.post("auth/refresh") { json(RefreshRequest(current.refreshToken)) }
        if (!response.status.isSuccess()) {
            store.save(null)
            return false
        }
        store.save(response.body<Tokens>())
        true
    }

    private inline fun <reified B> HttpRequestBuilder.json(body: B) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    /** The file name of a `Content-Disposition: attachment` header, without any folder part. */
    private fun HttpResponse.attachmentName(): String? =
        headers[HttpHeaders.ContentDisposition]?.let { runCatching { ContentDisposition.parse(it).parameter(ContentDisposition.Parameters.FileName) }.getOrNull() }
            ?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() }

    private suspend fun HttpResponse.toProblem(): Problem {
        val text = bodyAsText()
        return runCatching { DoraiJson.decodeFromString<Problem>(text) }
            .getOrElse { Problem(title = status.description, status = status.value, detail = text.ifBlank { null }) }
    }
}

/** A file the server sent and the name it gave it, when it gave one. */
class DownloadedFile(val fileName: String?, val bytes: ByteArray)
