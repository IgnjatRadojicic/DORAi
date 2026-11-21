package dorai.server.ai.services

import dorai.server.ai.chat.AiTask
import dorai.server.ai.chat.ChatModel
import dorai.server.core.AiAnswer
import dorai.server.core.DraftText
import dorai.server.core.FindingNote
import dorai.server.core.RegulationSearch
import dorai.server.core.ReportDrafter
import dorai.shared.model.Finding
import dorai.shared.model.Register
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Drafts the PDF report text: a summary and one sentence per rule finding, cited from the regulation.
 * The reply must name exactly the supplied findings (by rule and resource), so the model can neither
 * add nor drop one; severity is never part of the reply, so it cannot re-rate one either.
 */
class AiReportDrafter(chat: ChatModel, search: RegulationSearch) : ReportDrafter {
    private val grounded = GroundedAnswer(chat, search)

    override suspend fun draft(register: Register, findings: List<Finding>): AiAnswer<DraftText> {
        val supplied = findings.take(MAX_FINDINGS)
        val keys = supplied.map { it.key() }.distinct()
        return grounded.answer(
            task = AiTask.DraftReport,
            query = "register of information ${supplied.flatMap { it.templates }.distinct().joinToString(" ")}".trim(),
            limit = 5,
            data = buildJsonObject {
                putJsonObject("register") {
                    put("entity", register.entity.name)
                    put("providers", register.providers.size)
                    put("contracts", register.contracts.size)
                    put("functions", register.functions.size)
                    put("criticalFunctions", register.functions.count { it.isCritical })
                }
                putJsonArray("findings") {
                    supplied.forEach { finding ->
                        addJsonObject {
                            put("ruleId", finding.ruleId)
                            put("resourceAddress", finding.resourceAddress)
                            put("severity", finding.severity.name)
                            put("title", finding.title)
                            put("detail", finding.detail)
                            putJsonArray("templates") { finding.templates.forEach { add(JsonPrimitive(it)) } }
                        }
                    }
                }
            },
            serializer = ReportReply.serializer(),
            isValid = { reply, evidence ->
                val replyKeys = reply.findings.map { it.ruleId to it.resourceAddress }
                ReplyChecks.text(reply.summary, MAX_SUMMARY) &&
                    reply.findings.all { ReplyChecks.text(it.text) } &&
                    replyKeys.size == keys.size &&
                    replyKeys.toSet() == keys.toSet() &&
                    reply.citationIds.isNotEmpty() &&
                    ReplyChecks.citesOnly(reply.citationIds, evidence)
            },
            toAnswer = { reply, evidence ->
                val byKey = reply.findings.associateBy { it.ruleId to it.resourceAddress }
                val notes = keys.map { key -> FindingNote(key.first, key.second, byKey.getValue(key).text.trim()) }
                AiAnswer.Answer(
                    DraftText(reply.summary.trim(), notes),
                    "Drafted from the register and the rule findings.",
                    ReplyChecks.selected(reply.citationIds, evidence),
                )
            },
        )
    }

    private fun Finding.key() = ruleId to resourceAddress

    companion object {
        /** Findings beyond this many are left out of the draft and the prompt. */
        const val MAX_FINDINGS = 50
        private const val MAX_SUMMARY = 2000
    }
}
