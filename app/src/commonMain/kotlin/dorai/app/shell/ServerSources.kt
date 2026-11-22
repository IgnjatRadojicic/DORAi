package dorai.app.shell

import dorai.shared.model.ValidationIssue
import dorai.shared.roi.SERVICE_TYPES
import dorai.shared.roi.RoiView
import dorai.shared.roi.RoiItems
import dorai.app.roi.RoiSource
import dorai.app.admin.AdminSource
import dorai.app.register.RegisterSource
import dorai.shared.model.BusinessFunction
import dorai.shared.model.Contract
import dorai.shared.model.CreateUserRequest
import dorai.shared.model.FinancialEntity
import dorai.shared.model.Provider
import dorai.shared.model.RoiRow
import dorai.shared.model.Role
import dorai.shared.roi.RoiSpec
import dorai.app.components.EvidenceCitation
import dorai.app.components.FindingSeverity
import dorai.app.components.FrontendFailure
import dorai.app.components.Provenance
import dorai.app.export.ExportDocument
import dorai.app.export.ExportFormat
import dorai.app.export.ExportSource
import dorai.app.findings.ChangeFinding
import dorai.app.findings.FindingsSource
import dorai.app.inventory.InventoryResource
import dorai.app.inventory.InventorySource
import dorai.app.inventory.ScanSummary
import dorai.app.inventory.SourcedValue
import dorai.app.review.ReviewSource
import dorai.app.review.ReviewStatus
import dorai.app.review.ReviewSuggestion
import dorai.app.review.SuggestionField
import dorai.app.validation.IssueSeverity
import dorai.app.validation.RegisterIssue
import dorai.app.validation.RegisterValidation
import dorai.app.validation.ValidationSource
import dorai.shared.api.ApiResult
import dorai.shared.api.DoraiApi
import dorai.shared.model.ChangeAction
import dorai.shared.model.Citation
import dorai.shared.model.CostSource
import dorai.shared.model.Finding
import dorai.shared.model.Resource
import dorai.shared.model.ScanMode
import dorai.shared.model.ScanResult
import dorai.shared.model.ServiceType
import dorai.shared.model.Suggestion
import dorai.shared.model.SuggestionKind
import dorai.shared.model.SuggestionStatus
import dorai.shared.model.Severity
import dorai.shared.model.ValidationReport
import dorai.shared.model.ValidationSeverity

/**
 * Connects the workspace screens to the server through the shared client.
 * Wire values are mapped as they are: rule, severity, resource and citation never change on the way.
 */
class ServerSources(private val api: DoraiApi) {
    val findings = FindingsSource { plan ->
        api.check(plan.bytes, plan.name).orFail("The plan could not be checked.").map { it.toChangeFinding() }
    }

    /** The register checks and the RoI readiness (all 15 templates and the DORA checks) in one list. */
    val validation = ValidationSource {
        val register = api.validation().orFail("Validation could not be loaded.").toRegisterValidation()
        val roi = api.roi().orFail("The register of information could not be loaded.")
        register.withRoi(roi)
    }

    val roi = object : RoiSource {
        override suspend fun load() = api.roi().orFail("The register of information could not be loaded.")
        override suspend fun setValue(target: RoiItems.Target, column: String, value: String?) {
            when (target) {
                RoiItems.Target.Entity -> api.putEntityValue(column, value)
                is RoiItems.Target.Item -> api.putValue(target.id, column, value)
            }.orFail("The value could not be saved.")
        }
    }

    val register = object : RegisterSource {
        override suspend fun load() = api.register().orFail("The register could not be loaded.")
        override suspend fun saveEntity(entity: FinancialEntity) { api.putEntity(entity).orFail("The entity could not be saved.") }
        override suspend fun saveProvider(provider: Provider) { api.putProvider(provider).orFail("The provider could not be saved.") }
        override suspend fun deleteProvider(id: String) = api.deleteProvider(id).orFail("The provider could not be deleted.")
        override suspend fun saveContract(contract: Contract) { api.putContract(contract).orFail("The contract could not be saved.") }
        override suspend fun deleteContract(id: String) = api.deleteContract(id).orFail("The contract could not be deleted.")
        override suspend fun saveFunction(function: BusinessFunction) { api.putFunction(function).orFail("The function could not be saved.") }
        override suspend fun deleteFunction(id: String) = api.deleteFunction(id).orFail("The function could not be deleted.")
        override suspend fun saveRow(template: String, row: RoiRow) { api.putRow(template, row).orFail("The row could not be saved.") }
        override suspend fun deleteRow(template: String, id: String) = api.deleteRow(template, id).orFail("The row could not be deleted.")
    }

    val admin = object : AdminSource {
        override suspend fun users() = api.users().orFail("Users could not be loaded.")
        override suspend fun invites() = api.invites().orFail("Invites could not be loaded.")
        override suspend fun createUser(username: String, role: Role, temporaryPassword: String?) =
            api.createUser(CreateUserRequest(username, role, temporaryPassword)).orFail("The user could not be created.")
        override suspend fun disable(username: String) { api.disableUser(username).orFail("The user could not be disabled.") }
        override suspend fun revoke(username: String) = api.revokeSessions(username).orFail("The sessions could not be ended.")
        override suspend fun invite(username: String) = api.createInvite(username).orFail("The invite could not be created.")
    }

    /**
     * Each endpoint serves one fixed media type, so the format supplies it. The package keeps the name the
     * server gives it (the EBA filing name, `DRAFT_` in front of a draft), so a draft is never saved as final.
     */
    val export = ExportSource { format ->
        when (format) {
            // A draft until the register is complete, so the user can always see the package.
            ExportFormat.CSV -> {
                val file = api.exportCsvFile(draft = (api.roi() as? ApiResult.Success)?.value?.ready != true).orFail("The export could not be downloaded.")
                ExportDocument(file.bytes, format.mediaType, file.fileName)
            }
            ExportFormat.PDF -> ExportDocument(api.exportPdf().orFail("The export could not be downloaded."), format.mediaType)
        }
    }

    val inventory = object : InventorySource {
        override suspend fun resources(): List<InventoryResource> {
            val resources = api.resources().orFail("The inventory could not be loaded.")
            val suggestions = api.suggestions().orFail("The inventory could not be loaded.")
            val functions = functionNames()
            return resources.map { it.toInventoryResource(suggestions, functions) }
        }

        override suspend fun scan(): ScanSummary = api.scan().orFail("The scan did not finish.").toScanSummary()
    }

    val review = object : ReviewSource {
        override suspend fun suggestions(): List<ReviewSuggestion> {
            val functions = functionNames()
            return api.suggestions().orFail("The suggestions could not be loaded.").map { it.toReviewSuggestion(functions) }
        }

        override suspend fun generate() {
            api.generateSuggestions().orFail("The AI could not make suggestions.")
        }

        override suspend fun decide(id: String, accept: Boolean): ReviewSuggestion {
            val result = if (accept) api.acceptSuggestion(id) else api.rejectSuggestion(id)
            return result.orFail("The decision was not saved.").toReviewSuggestion(functionNames())
        }
    }

    /** Business function names from the register, so F1 shows as "F1 Payments". */
    private suspend fun functionNames(): Map<String, String> =
        api.register().orFail("The register could not be loaded.").functions.associate { it.id to it.name }
}

/** The value, or a [FrontendFailure] with a message that is safe to show (never the server's detail text). */
internal fun <T> ApiResult<T>.orFail(fallback: String): T = when (this) {
    is ApiResult.Success -> value
    is ApiResult.Failure -> throw FrontendFailure(
        when (problem.status) {
            0 -> "The server did not answer. Check the connection and retry."
            401, 403 -> "Your role does not allow this action."
            400, 413, 415, 422 -> "$fallback The server could not use what was sent (HTTP ${problem.status})."
            else -> "$fallback The server answered with HTTP ${problem.status}."
        },
        retryable = problem.status == 0 || problem.status >= 500,
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

/** Adds the RoI issues the register checks do not already report. */
internal fun RegisterValidation.withRoi(roi: RoiView): RegisterValidation {
    val known = issues.map { Triple(it.code, it.field, it.entityId) }.toSet()
    val extra = roi.issues.filter { Triple(it.code, it.field, it.entityId) !in known }.map { it.toRegisterIssue() }
    val all = issues + extra
    val errors = all.count { it.severity == IssueSeverity.ERROR }
    return RegisterValidation(isValid = errors == 0, errorCount = errors, warningCount = all.size - errors, issues = all)
}

internal fun ValidationIssue.toRegisterIssue() = RegisterIssue(
    severity = if (severity == ValidationSeverity.Error) IssueSeverity.ERROR else IssueSeverity.WARNING,
    code = code, template = template, field = field, message = message, entityId = entityId,
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

/**
 * A scanned value is "scanned". Where the scan found none, an accepted suggestion shows as
 * "human-confirmed" and a pending one as "ai-suggested"; a rejected one is not shown.
 */
internal fun Resource.toInventoryResource(suggestions: List<Suggestion>, functionNames: Map<String, String>): InventoryResource {
    val own = suggestions.filter { it.resourceId == id }
    fun sourced(scanned: String?, kind: SuggestionKind): SourcedValue? {
        if (scanned != null) return SourcedValue(scanned, Provenance.SCANNED)
        val accepted = own.firstOrNull { it.kind == kind && it.status == SuggestionStatus.Accepted }
        val pending = own.firstOrNull { it.kind == kind && it.status == SuggestionStatus.Pending }
        return accepted?.let { SourcedValue(it.value, Provenance.HUMAN_CONFIRMED) }
            ?: pending?.let { SourcedValue(it.value, Provenance.AI_SUGGESTED) }
    }
    fun withName(value: SourcedValue?) = value?.copy(label = functionNames[value.value])
    fun withServiceName(value: SourcedValue?) = value?.copy(label = serviceTypeName(value.value))
    return InventoryResource(
        id = id,
        name = name,
        type = type,
        provider = provider,
        location = location,
        country = country,
        serviceType = withServiceName(sourced(serviceType?.takeIf { it != ServiceType.Unknown }?.name, SuggestionKind.ServiceType)),
        function = withName(sourced(functionId, SuggestionKind.BusinessFunction)),
        monthlyCost = monthlyCost,
        currency = currency,
        tags = tags,
        redundancy = redundancy.name,
        source = source.name,
        changeAction = changeAction.takeIf { it != ChangeAction.None }?.name,
        contractId = contractId,
    )
}

internal fun ScanResult.toScanSummary() = ScanSummary(
    live = mode == ScanMode.Live,
    scannedAt = scannedAt.toString(),
    resourceCount = resourceCount,
    newSuggestionCount = newSuggestionCount,
    liveCost = costSource == CostSource.Live,
)

internal fun Suggestion.toReviewSuggestion(functionNames: Map<String, String>) = ReviewSuggestion(
    id = id,
    resourceName = resourceName,
    field = when (kind) {
        SuggestionKind.ServiceType -> SuggestionField.SERVICE_TYPE
        SuggestionKind.BusinessFunction -> SuggestionField.BUSINESS_FUNCTION
        SuggestionKind.RoiValue -> SuggestionField.ROI_VALUE
    },
    value = value,
    valueLabel = when (kind) {
        SuggestionKind.BusinessFunction -> functionNames[value]
        SuggestionKind.RoiValue -> column?.let { RoiSpec.column(it)?.label(value) }
        SuggestionKind.ServiceType -> serviceTypeName(value)
        else -> null
    },
    reason = reason,
    citations = citations.map { it.toEvidence() },
    confidence = confidence,
    status = when (status) {
        SuggestionStatus.Pending -> ReviewStatus.PENDING
        SuggestionStatus.Accepted -> ReviewStatus.ACCEPTED
        SuggestionStatus.Rejected -> ReviewStatus.REJECTED
    },
    fieldLabel = column?.let { c -> RoiSpec.column(c)?.let { "${it.code} ${it.name}" } ?: c },
)

/** "S18" shows as "S18 Cloud services: PaaS": a code never stands alone. */
internal fun serviceTypeName(code: String): String? = SERVICE_TYPES.firstOrNull { it.key == code }?.label
