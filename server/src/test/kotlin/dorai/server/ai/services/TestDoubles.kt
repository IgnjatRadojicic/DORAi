package dorai.server.ai.services

import dorai.server.ai.chat.ChatModel
import dorai.server.ai.chat.ChatRequest
import dorai.server.ai.chat.InvalidReplyException
import dorai.server.core.RegulationSearch
import dorai.shared.model.Citation
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Regulation search that returns fixed passages, or throws when [failure] is set. */
class StubSearch(private vararg val citations: Citation, private val failure: Exception? = null) : RegulationSearch {
    val queries = mutableListOf<String>()

    override suspend fun search(query: String, limit: Int): List<Citation> {
        queries += query
        failure?.let { throw it }
        return citations.take(limit)
    }
}

/** Chat model that plays back replies in order: a JSON string is parsed, an exception is thrown. */
class ScriptedChat(vararg replies: Any) : ChatModel {
    private val queue = ArrayDeque(replies.toList())
    val requests = mutableListOf<ChatRequest>()
    override val modelId = "test/scripted"

    override suspend fun <T> ask(request: ChatRequest, serializer: KSerializer<T>): T {
        requests += request
        return when (val next = queue.removeFirstOrNull() ?: error("No scripted reply left")) {
            is Exception -> throw next
            is String -> try {
                Json.decodeFromString(serializer, next)
            } catch (e: SerializationException) {
                throw InvalidReplyException("unparseable", e)
            }
            else -> error("Unsupported scripted reply $next")
        }
    }
}

fun citation(id: String, article: String = "Annex III", excerpt: String = "Passage $id") =
    Citation(id = id, regulation = id.substringBefore('/'), article = article, excerpt = excerpt, score = 0.5)

val annexS17 = citation("CIR-2024-2956/AnnexIII/S17", excerpt = "17. Cloud services: IaaS")
val annexS18 = citation("CIR-2024-2956/AnnexIII/S18", excerpt = "18. Cloud services: PaaS")
val functionsColumn = citation("CIR-2024-2956/AnnexI/B_06.01.0010", article = "Annex I", excerpt = "Function identifier")
val doraArticle = citation("DORA/Art-28/3", article = "Article 28", excerpt = "Register of information")
