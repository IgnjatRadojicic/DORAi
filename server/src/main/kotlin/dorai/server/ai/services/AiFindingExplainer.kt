package dorai.server.ai.services

import dorai.server.ai.chat.AiTask
import dorai.server.ai.chat.ChatModel
import dorai.server.core.AiAnswer
import dorai.server.core.FindingExplainer
import dorai.server.core.RegulationSearch
import dorai.shared.model.Finding
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Explains in one sentence why a finding matters, cited from the regulation. It returns text only,
 * so it cannot create, remove or re-rate a finding: rule, severity and resource stay the rule's.
 */
class AiFindingExplainer(chat: ChatModel, search: RegulationSearch) : FindingExplainer {
    private val grounded = GroundedAnswer(chat, search)

    override suspend fun explain(finding: Finding): AiAnswer<String> = grounded.answer(
        task = AiTask.ExplainFinding,
        query = "${finding.templates.joinToString(" ")} ${finding.title} ${finding.detail}",
        limit = 3,
        data = buildJsonObject {
            putJsonObject("finding") {
                put("ruleId", finding.ruleId)
                put("severity", finding.severity.name)
                put("title", finding.title)
                put("detail", finding.detail)
                put("resourceAddress", finding.resourceAddress)
                putJsonArray("templates") { finding.templates.forEach { add(JsonPrimitive(it)) } }
                put("location", finding.location)
                put("functionName", finding.functionName)
                put("criticalFunction", finding.criticalFunction)
            }
        },
        serializer = ExplanationReply.serializer(),
        isValid = { reply, evidence ->
            ReplyChecks.text(reply.explanation) &&
                reply.citationIds.isNotEmpty() &&
                ReplyChecks.citesOnly(reply.citationIds, evidence)
        },
        toAnswer = { reply, evidence ->
            AiAnswer.Answer(reply.explanation.trim(), "Explained from the cited regulation passage.", ReplyChecks.selected(reply.citationIds, evidence))
        },
    )
}
