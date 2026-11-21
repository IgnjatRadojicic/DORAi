package dorai.server.core

import dorai.shared.model.BusinessFunction
import dorai.shared.model.Citation
import dorai.shared.model.Finding
import dorai.shared.model.Register
import dorai.shared.model.Resource
import dorai.shared.model.ServiceType

/**
 * What an AI service returns. An answer always carries at least one citation;
 * without evidence the service says so instead of guessing.
 */
sealed interface AiAnswer<out T> {
    data class Answer<out T>(
        val value: T,
        val reason: String,
        val citations: List<Citation>,
        val confidence: Double? = null,
    ) : AiAnswer<T> {
        init {
            require(citations.isNotEmpty()) { "An AI answer must cite the regulation" }
        }
    }

    /** Search found nothing relevant enough to support an answer. */
    data class InsufficientEvidence(val reason: String) : AiAnswer<Nothing>

    /** The model failed or timed out; the caller shows this, never a made-up answer. */
    data class Failed(val reason: String) : AiAnswer<Nothing>
}

/** Proposes the ICT service type of a resource the type table does not know. */
interface ResourceClassifier {
    suspend fun classify(resource: Resource): AiAnswer<ServiceType>
}

/** Proposes which business function a resource supports; the answer is a function id. */
interface FunctionSuggester {
    suspend fun suggest(resource: Resource, functions: List<BusinessFunction>): AiAnswer<String>
}

/** Explains a finding the rules produced. Never creates, removes or changes a finding. */
interface FindingExplainer {
    suspend fun explain(finding: Finding): AiAnswer<String>
}

/** Writes the summary and one sentence per finding for the PDF report. Never adds, drops or re-rates a finding. */
interface ReportDrafter {
    suspend fun draft(register: Register, findings: List<Finding>): AiAnswer<DraftText>
}

/** The drafted report text: a summary and one note per supplied finding, in the rules' order. */
data class DraftText(val summary: String, val findings: List<FindingNote>)

/** One drafted sentence about a finding, identified by the finding's rule and resource. */
data class FindingNote(val ruleId: String, val resourceAddress: String, val text: String)

/** Finds regulation passages for a question, best first, above the similarity threshold. */
interface RegulationSearch {
    suspend fun search(query: String, limit: Int = 5): List<Citation>
}
