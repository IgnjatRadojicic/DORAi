package dorai.server.ai.services

import kotlinx.serialization.Serializable

/** What the chat model must answer for each task. Koog turns these into the JSON schema it sends. */

@Serializable
data class ClassificationReply(val serviceType: String, val reason: String, val confidence: Double, val citationIds: List<String>)

@Serializable
data class FunctionReply(val functionId: String, val reason: String, val confidence: Double, val citationIds: List<String>)

@Serializable
data class ExplanationReply(val explanation: String, val citationIds: List<String>)

@Serializable
data class ReportReply(val summary: String, val findings: List<FindingSentence>, val citationIds: List<String>)

@Serializable
data class FindingSentence(val ruleId: String, val resourceAddress: String, val text: String)
