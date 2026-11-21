package dorai.server.export

import dorai.server.core.AiAnswer
import dorai.server.core.DraftText
import dorai.server.core.ReportDrafter
import dorai.shared.model.Citation
import dorai.shared.model.Finding
import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.Register
import dorai.shared.model.RegisterField
import dorai.shared.model.ValidationReport
import dorai.shared.model.ValidationSeverity
import dorai.shared.roi.RoiSpec
import dorai.shared.roi.RoiView
import dorai.shared.rules.RegisterValidator
import dorai.shared.rules.RulesRegisterValidator
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** A diagnostic report is available even when register validation fails. */
class RegisterPdfReport(
    private val drafter: ReportDrafter? = null,
    private val validator: RegisterValidator = RulesRegisterValidator(),
    private val clock: Clock = Clock.System,
    private val draftTimeoutMillis: Long = 10_000,
) {
    /** [roi]: the 15 RoI templates and their readiness; without it the report covers the register only. */
    suspend fun generate(register: Register, findings: List<Finding>, roi: RoiView? = null): ByteArray {
        val validation = validator.validate(register)
        val draft = draft(register, findings)
        val prepared = clock.now().toString()
        return withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            context.ensureActive()
            val output = ByteArrayOutputStream()
            val citations = linkedSetOf<Citation>()
            ReportLayout(output).use { page ->
                page.title("Register of information", "ICT register review")
                page.text(register.entity.name, 15f, true)
                page.note(register.entity.country + "  /  LEI " + register.entity.lei)
                page.note("Register as of " + register.asOf + "\nPrepared " + prepared)
                page.table(listOf("Providers", "Contracts", "Functions", "Critical functions"), floatArrayOf(1f, 1f, 1f, 1.3f),
                    listOf(listOf(register.providers.size.toString(), register.contracts.size.toString(),
                        register.functions.size.toString(), register.functions.count { it.isCritical }.toString())))
                page.heading("01", "Review summary")
                page.text(if (validation.isValid) "Basic validation: passed" else "Basic validation: action required", 12f, true)
                page.note("${validation.errorCount} errors / ${validation.warningCount} warnings / ${findings.size} rule findings")
                page.note("Checks cover the current register model. They do not establish full DORA compliance. This report is not a regulatory filing.")
                if (draft == null) {
                    page.note("AI summary unavailable: insufficient evidence or the drafting service is unavailable. Register data and rule findings remain included.")
                } else {
                    page.text("AI-suggested summary / awaiting human confirmation", 9f, true)
                    page.text(draft.value.summary)
                    page.note("Supporting references: " + draft.citations.joinToString { it.id })
                    citations.addAll(draft.citations)
                }
                page.heading("02", "Annual contract costs")
                val total = completeTotal(register, validation)
                page.text("Annual register expense: " + money(total, register.contracts.firstOrNull()?.currency), 16f, true)
                page.table(listOf("Contract / provider", "Annual expense", "Source"), floatArrayOf(2.6f, 1.5f, 1.5f),
                    register.contracts.sortedBy { it.id }.map { contract ->
                        context.ensureActive()
                        listOf(contract.id + " / " + (register.providers.firstOrNull { it.id == contract.providerId }?.name ?: "Unknown provider"),
                            money(ExportCells.amount(contract.annualCost)?.takeIf { it.signum() >= 0 }, contract.currency), label(contract.annualCost))
                    })
                page.heading("03", "Provider cost share")
                if (total == null) page.note("Overall total and provider shares: not available. Complete non-negative amounts, one currency and valid register references are required.")
                else {
                    page.table(listOf("Provider", "Annual expense", "Share"), floatArrayOf(2.5f, 1.5f, 1f),
                        register.providers.sortedBy { it.id }.map { provider ->
                            val amount = register.contracts.filter { it.providerId == provider.id }
                                .fold(BigDecimal.ZERO) { sum, contract -> sum + checkNotNull(ExportCells.amount(contract.annualCost)) }
                            listOf(provider.name, money(amount, register.contracts.first().currency),
                                if (total.signum() == 0) "Not available" else amount.multiply(BigDecimal(100)).divide(total, 2, RoundingMode.HALF_UP).toPlainString() + "%")
                        })
                    page.note("Recorded annual contract expense divided by total annual expense. Cost share is not an operational risk score. Percentage shares are unavailable for a zero total.")
                }
                page.page()
                page.title("Coverage and follow-up", "Register and findings")
                page.heading("04", "Business functions")
                page.table(listOf("Function", "Critical", "Linked services"), floatArrayOf(3.5f, 1f, 1.3f),
                    register.functions.sortedBy { it.id }.map { function ->
                        listOf(function.id + " / " + function.name, if (function.isCritical) "Yes" else "No",
                            register.contracts.flatMap { it.services }.count { ExportCells.text(it.functionId) == function.id }.toString())
                    })
                page.heading("05", "Register validation")
                if (validation.issues.isEmpty()) page.note("No validation issues. Required fields and register links passed the current checks.")
                else validation.issues.sortedWith(compareBy({ it.severity.name }, { it.template }, { it.entityId }, { it.field })).forEach { issue ->
                    context.ensureActive()
                    page.text(issue.severity.name.uppercase() + " / " + issue.code, 9f, true, before = 8f)
                    val pendingFunction = issue.code == "unknown-function" && register.contracts.flatMap { it.services }.any {
                        it.id == issue.entityId && it.functionId.provenance.label == ProvenanceLabel.AiSuggested
                    }
                    page.text(if (pendingFunction) "The proposed function is unconfirmed and does not match a registered business function. Proposed value omitted." else issue.message)
                    page.note(listOfNotNull(issue.template, issue.field, issue.entityId).joinToString(" / "))
                }
                val audit = manifest(register)
                page.heading("06", "Data awaiting review")
                page.text("Fields awaiting human confirmation", 9f, true)
                page.note(audit.omittedAiSuggestedFields.joinToString("\n").ifEmpty { "None" })
                page.text("Missing register data", 9f, true)
                page.note(audit.missingKnownFields.joinToString("\n").ifEmpty { "None" })
                page.heading("07", "Rule findings")
                page.note("Findings below are produced by deterministic rules. The AI cannot add, remove or change their severity.")
                if (findings.isEmpty()) page.note("No findings were supplied for this report. This does not establish the absence of all risks.")
                val notes = draft?.value?.findings.orEmpty().associateBy { it.ruleId to it.resourceAddress }
                findings.forEach { finding ->
                    context.ensureActive()
                    page.reserve(145f)
                    page.text(finding.severity.name.uppercase() + " / " + finding.ruleId + " / " + finding.title, 10f, true, before = 12f)
                    page.note(finding.resourceAddress)
                    page.text(finding.detail)
                    if (finding.templates.isNotEmpty()) page.note("Register templates: " + finding.templates.joinToString())
                    if (finding.criticalFunction) page.note("Supports a critical function: " + (finding.functionName ?: finding.functionId ?: "Recorded critical function"))
                    val note = notes[finding.ruleId to finding.resourceAddress]?.text
                    if (note != null) {
                        page.text("AI-suggested note: " + note)
                        page.note("Supporting references: " + draft!!.citations.joinToString { it.id })
                    }
                    val citation = finding.citation?.takeIf(::usable)
                    if (citation != null) {
                        citations += citation
                        finding.explanation?.takeIf { it.isNotBlank() }?.let { page.text("Cited explanation: " + it) }
                        page.note("Stored reference: " + citation.id)
                    }
                }
                if (roi != null) registerOfInformation(page, roi)
                page.page()
                page.title("Appendix", "Sources and methodology")
                page.heading("A", "Basis of preparation")
                page.text("Amounts come from recorded annual contract values, without a new billing query. No currency conversion or partial overall totals are used. Where fixture data is used, identities and expenses are synthetic. Scanned, planned and human-confirmed values retain their source label. Unconfirmed AI field values are omitted from exported values and calculations.")
                page.text("Entity, provider, contract and function identifiers have no field-level provenance in this model. Cost per function is not available because resource costs have not been allocated to business functions. Unsupported regulatory fields are listed in the CSV manifest.")
                page.text("Findings use the plan configured on this server. The export endpoint does not retain a previously uploaded plan or perform a live infrastructure comparison. AI text is an unconfirmed draft and is included only with supporting stored references.")
                page.heading("B", "Field source record")
                fields(register).groupBy { it.second.provenance }.forEach { (source, entries) ->
                    context.ensureActive()
                    page.text(label(entries.first().second) + " / " + source.updatedAt, 9f, true, before = 10f)
                    page.note(entries.joinToString("\n") { it.first })
                    // An unaccepted proposal's reason may repeat its value, so omit it too.
                    if (source.label != ProvenanceLabel.AiSuggested) {
                        source.reason?.takeIf { it.isNotBlank() }?.let { page.text(it, 8f) }
                        source.citation?.takeIf(::usable)?.let { citations += it; page.note("Stored reference: " + it.id) }
                    }
                }
                page.heading("C", "Regulatory references")
                if (citations.isEmpty()) page.note("No regulatory citations were supplied. No legal references have been inferred.")
                citations.forEach { citation ->
                    context.ensureActive()
                    page.text(citation.id + " / " + citation.regulation + " / " + citation.article +
                        (citation.paragraph?.let { " / " + it } ?: ""), 9f, true, before = 10f)
                    page.text(citation.excerpt, 8f)
                }
            }
            context.ensureActive()
            output.toByteArray()
        }
    }

    /** Section 08: each RoI template as the supervisor's package carries it, with where every value came from. */
    private suspend fun registerOfInformation(page: ReportLayout, roi: RoiView) {
        val context = currentCoroutineContext()
        page.page()
        page.title("Register of Information", "The 15 templates")
        page.heading("08", "Readiness on ${roi.referenceDate}")
        page.text(if (roi.ready) "Ready for the final package" else "Not ready: the final package is refused, a draft is available", 12f, true)
        page.note("${roi.errorCount} errors / ${roi.warningCount} warnings. Unaccepted AI suggestions are left out of every template.")
        val issues = roi.issues.groupBy { it.template }
        page.table(listOf("Template", "Name", "Rows", "Errors", "Warnings"), floatArrayOf(1f, 3.6f, 0.7f, 0.8f, 1f),
            RoiSpec.templates.map { t ->
                val own = issues[t.code].orEmpty()
                listOf(t.code, t.name, roi.tables[t.code].orEmpty().size.toString(),
                    own.count { it.severity == ValidationSeverity.Error }.toString(), own.count { it.severity == ValidationSeverity.Warning }.toString())
            })
        RoiSpec.templates.forEach { t ->
            context.ensureActive()
            val rows = roi.tables[t.code].orEmpty()
            page.reserve(90f)
            page.text(t.code + " / " + t.name, 10f, true, before = 12f)
            if (rows.isEmpty()) {
                page.note("No rows.")
                return@forEach
            }
            page.table(listOf("Row", "Column", "Value", "Source"), floatArrayOf(1.2f, 3f, 2f, 1.1f),
                rows.flatMap { row ->
                    t.columns.mapNotNull { col ->
                        val cell = row.cells[col.code] ?: return@mapNotNull null
                        listOf(row.key, col.header + " " + col.name, cell.value, cell.label?.let(::source) ?: "derived")
                    }
                })
            issues[t.code].orEmpty().filter { it.severity == ValidationSeverity.Error }.forEach { page.note("Error: " + it.message) }
        }
    }

    private fun source(label: ProvenanceLabel) = when (label) {
        ProvenanceLabel.Scanned -> "scanned"
        ProvenanceLabel.Planned -> "planned"
        ProvenanceLabel.AiSuggested -> "ai-suggested"
        ProvenanceLabel.HumanConfirmed -> "human-confirmed"
    }

    private suspend fun draft(register: Register, findings: List<Finding>): AiAnswer.Answer<DraftText>? {
        val drafter = drafter ?: return null
        val answer = try {
            withTimeoutOrNull(draftTimeoutMillis) { drafter.draft(register, findings) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (answer !is AiAnswer.Answer || answer.value.summary.isBlank() || answer.citations.any { !usable(it) }) return null
        val allowed = findings.take(50).map { it.ruleId to it.resourceAddress }.toSet()
        val notes = answer.value.findings
        val keys = notes.map { it.ruleId to it.resourceAddress }
        if (keys.toSet() != allowed || keys.distinct().size != keys.size || notes.any { it.text.isBlank() }) return null
        return answer
    }

    private fun completeTotal(register: Register, validation: ValidationReport): BigDecimal? {
        if (!validation.isValid || validation.errorCount > 0 || register.contracts.isEmpty()) return null
        if (register.contracts.any { contract ->
                ExportCells.amount(contract.annualCost)?.signum()?.let { it < 0 } != false || contract.currency.isNullOrBlank() ||
                    register.providers.none { it.id == contract.providerId }
            } || register.contracts.map { it.currency }.distinct().size != 1) return null
        return register.contracts.fold(BigDecimal.ZERO) { sum, contract -> sum + checkNotNull(ExportCells.amount(contract.annualCost)) }
    }

    private fun money(amount: BigDecimal?, currency: String?): String =
        if (amount == null || currency.isNullOrBlank()) "Not available" else amount.stripTrailingZeros().toPlainString() + " " + currency

    private fun label(field: RegisterField): String = when (field.provenance.label) {
        ProvenanceLabel.Scanned -> "Scanned"
        ProvenanceLabel.Planned -> "Planned"
        ProvenanceLabel.AiSuggested -> "AI-suggested / omitted"
        ProvenanceLabel.HumanConfirmed -> "Human-confirmed"
    }

    private fun usable(citation: Citation) = citation.id.isNotBlank() && citation.regulation.isNotBlank() && citation.article.isNotBlank() && citation.excerpt.isNotBlank()

    private fun fields(register: Register): List<Pair<String, RegisterField>> = buildList {
        register.providers.sortedBy { it.id }.forEach { provider ->
            add("Provider/${provider.id}/LEI" to provider.lei)
            add("Provider/${provider.id}/HeadquartersCountry" to provider.headquartersCountry)
        }
        register.contracts.sortedBy { it.id }.forEach { contract ->
            add("Contract/${contract.id}/AnnualCost" to contract.annualCost)
            contract.services.sortedBy { it.id }.forEach { service ->
                add("Service/${service.id}/ServiceType" to service.serviceType)
                add("Service/${service.id}/FunctionId" to service.functionId)
                add("Service/${service.id}/DataLocation" to service.dataLocation)
            }
        }
    }
}
