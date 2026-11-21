package dorai.server.ai.services

import dorai.server.ai.chat.AiTask
import dorai.server.ai.chat.ChatModel
import dorai.server.core.AiAnswer
import dorai.server.core.RegulationSearch
import dorai.server.core.RoiValueDrafter
import dorai.shared.roi.RoiColumn
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Drafts one judgement value of the register (data sensitivity, reliance, licensed activity,
 * substitutability) for one register item, cited from the regulation. The answer must be one of the
 * column's options, or "unknown" for insufficient evidence. A reviewer accepts or rejects every draft.
 */
class AiRoiValueDrafter(chat: ChatModel, search: RegulationSearch) : RoiValueDrafter {
    private val grounded = GroundedAnswer(chat, search)

    override suspend fun draftValue(column: RoiColumn, itemName: String, facts: Map<String, String>): AiAnswer<String> {
        if (column.options.isEmpty()) return AiAnswer.InsufficientEvidence("${column.code} has no options to choose from.")
        return grounded.answer(
            task = AiTask.DraftRoiValue,
            query = "${column.code} ${column.name}",
            limit = 3,
            data = buildJsonObject {
                putJsonObject("column") {
                    put("code", column.code)
                    put("name", column.name)
                    putJsonArray("options") { column.options.forEach { o -> addJsonObject { put("key", o.key); put("label", o.label) } } }
                }
                putJsonObject("item") {
                    put("name", itemName)
                    facts.forEach { (k, v) -> put(k, v) }
                }
            },
            serializer = RoiValueReply.serializer(),
            isValid = { reply, evidence ->
                val unknown = reply.value.equals(UNKNOWN, ignoreCase = true)
                ReplyChecks.text(reply.reason) &&
                    ReplyChecks.confidence(reply.confidence) &&
                    (unknown || (column.options.any { it.key == reply.value } && reply.citationIds.isNotEmpty())) &&
                    ReplyChecks.citesOnly(reply.citationIds, evidence)
            },
            toAnswer = { reply, evidence ->
                if (reply.value.equals(UNKNOWN, ignoreCase = true)) AiAnswer.InsufficientEvidence(reply.reason.trim())
                else AiAnswer.Answer(reply.value, reply.reason.trim(), ReplyChecks.selected(reply.citationIds, evidence), reply.confidence)
            },
        )
    }

    private companion object {
        const val UNKNOWN = "unknown"
    }
}
