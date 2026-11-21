package dorai.server.ai.services

import dorai.server.ai.chat.AiTask
import dorai.server.ai.chat.ChatModel
import dorai.server.core.AiAnswer
import dorai.server.core.FunctionSuggester
import dorai.server.core.RegulationSearch
import dorai.shared.model.BusinessFunction
import dorai.shared.model.Resource
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Proposes which of the register's business functions a resource supports, cited from the regulation.
 * It never invents a function; "unknown" is "insufficient evidence". A reviewer accepts or rejects the answer.
 */
class AiFunctionSuggester(chat: ChatModel, search: RegulationSearch) : FunctionSuggester {
    private val grounded = GroundedAnswer(chat, search)

    override suspend fun suggest(resource: Resource, functions: List<BusinessFunction>): AiAnswer<String> {
        if (functions.isEmpty()) return AiAnswer.InsufficientEvidence("The register has no business functions to choose from.")
        return grounded.answer(
            task = AiTask.SuggestFunction,
            query = EVIDENCE_QUERY,
            limit = 3,
            data = buildJsonObject {
                putJsonObject("resource") { putResource(resource) }
                putJsonArray("functions") {
                    functions.forEach { function ->
                        addJsonObject {
                            put("id", function.id)
                            put("name", function.name)
                            put("critical", function.isCritical)
                        }
                    }
                }
            },
            serializer = FunctionReply.serializer(),
            isValid = { reply, evidence ->
                val known = functions.any { it.id == reply.functionId }
                ReplyChecks.text(reply.reason) &&
                    ReplyChecks.confidence(reply.confidence) &&
                    (reply.functionId.equals(UNKNOWN, ignoreCase = true) || (known && reply.citationIds.isNotEmpty())) &&
                    ReplyChecks.citesOnly(reply.citationIds, evidence)
            },
            toAnswer = { reply, evidence ->
                if (reply.functionId.equals(UNKNOWN, ignoreCase = true)) {
                    AiAnswer.InsufficientEvidence(reply.reason.trim())
                } else {
                    AiAnswer.Answer(reply.functionId, reply.reason.trim(), ReplyChecks.selected(reply.citationIds, evidence), reply.confidence)
                }
            },
        )
    }

    private companion object {
        const val UNKNOWN = "unknown"
        const val EVIDENCE_QUERY =
            "B_06.01 functions identification function identifier B_02.02.0050 critical or important function"
    }
}
