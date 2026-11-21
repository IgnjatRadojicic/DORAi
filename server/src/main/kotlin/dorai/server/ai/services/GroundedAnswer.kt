package dorai.server.ai.services

import dorai.server.ai.chat.AiTask
import dorai.server.ai.chat.ChatModel
import dorai.server.ai.chat.ChatRequest
import dorai.server.ai.chat.ChatUnavailableException
import dorai.server.ai.chat.InvalidReplyException
import dorai.server.core.AiAnswer
import dorai.server.core.RegulationSearch
import dorai.shared.model.Citation
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

/**
 * The flow every AI service follows:
 * 1. search the regulation; nothing above the threshold is "insufficient evidence", no model call;
 * 2. ask the chat model with our instructions and the untrusted data plus evidence in a separate message;
 * 3. check the reply (cited ids must come from the evidence); one retry on a bad reply, then a failure.
 * A model or search outage is a failure, never an answer.
 */
internal class GroundedAnswer(private val chat: ChatModel, private val search: RegulationSearch) {

    suspend fun <R, T> answer(
        task: AiTask,
        query: String,
        limit: Int,
        data: JsonObject,
        serializer: KSerializer<R>,
        isValid: (R, List<Citation>) -> Boolean,
        toAnswer: (R, List<Citation>) -> AiAnswer<T>,
    ): AiAnswer<T> {
        val evidence = try {
            search.search(query, limit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Regulation search failed for {}", task.id, e)
            return AiAnswer.Failed(SEARCH_UNAVAILABLE)
        }
        if (evidence.isEmpty()) return AiAnswer.InsufficientEvidence(INSUFFICIENT_EVIDENCE)

        val request = ChatRequest(task, Prompts.instructions(task), withEvidence(data, evidence))
        repeat(ATTEMPTS) { attempt ->
            val reply = try {
                chat.ask(request, serializer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: InvalidReplyException) {
                log.info("Unparseable reply for {} (attempt {})", task.id, attempt + 1)
                null
            } catch (e: ChatUnavailableException) {
                log.warn("Chat model unavailable for {}", task.id, e)
                return AiAnswer.Failed(MODEL_UNAVAILABLE)
            } catch (e: Exception) {
                log.error("Chat model call failed for {}", task.id, e)
                return AiAnswer.Failed(MODEL_UNAVAILABLE)
            }
            if (reply != null && isValid(reply, evidence)) return toAnswer(reply, evidence)
            if (reply != null) log.info("Reply for {} failed the checks (attempt {})", task.id, attempt + 1)
        }
        return AiAnswer.Failed(INVALID_REPLY)
    }

    /** Evidence as the model sees it: id, source and a bounded excerpt. */
    private fun withEvidence(data: JsonObject, evidence: List<Citation>) = JsonObject(
        data + ("evidence" to JsonArray(
            evidence.map { citation ->
                buildJsonObject {
                    put("id", citation.id)
                    put("source", listOfNotNull(citation.regulation, citation.article, citation.paragraph).joinToString(", "))
                    put("excerpt", citation.excerpt.take(MAX_EXCERPT))
                }
            },
        )),
    )

    companion object {
        const val INSUFFICIENT_EVIDENCE = "Insufficient evidence: no regulation passage matched closely enough."
        const val SEARCH_UNAVAILABLE = "Regulation search is unavailable."
        const val MODEL_UNAVAILABLE = "The AI model is unavailable."
        const val INVALID_REPLY = "The AI model did not give a valid, cited answer."
        private const val ATTEMPTS = 2
        private const val MAX_EXCERPT = 800
        private val log = LoggerFactory.getLogger(GroundedAnswer::class.java)
    }
}

/** Checks shared by the services. */
internal object ReplyChecks {
    const val MAX_TEXT = 1000

    fun text(value: String, max: Int = MAX_TEXT) = value.isNotBlank() && value.length <= max

    fun confidence(value: Double) = value.isFinite() && value in 0.0..1.0

    /** Every cited id must be one of the supplied evidence ids. */
    fun citesOnly(ids: List<String>, evidence: List<Citation>) = ids.all { id -> evidence.any { it.id == id } }

    /** The cited passages, in the order the model cited them, without repeats. */
    fun selected(ids: List<String>, evidence: List<Citation>) = ids.distinct().mapNotNull { id -> evidence.firstOrNull { it.id == id } }
}
