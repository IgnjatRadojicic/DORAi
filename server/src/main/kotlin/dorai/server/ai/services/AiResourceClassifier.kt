package dorai.server.ai.services

import dorai.server.ai.chat.AiTask
import dorai.server.ai.chat.ChatModel
import dorai.server.core.AiAnswer
import dorai.server.core.RegulationSearch
import dorai.server.core.ResourceClassifier
import dorai.shared.model.Resource
import dorai.shared.model.ServiceType
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Proposes the Annex III service type of a resource, cited from the regulation. Unknown is "insufficient evidence". */
class AiResourceClassifier(chat: ChatModel, search: RegulationSearch) : ResourceClassifier {
    private val grounded = GroundedAnswer(chat, search)

    override suspend fun classify(resource: Resource): AiAnswer<ServiceType> = grounded.answer(
        task = AiTask.ClassifyServiceType,
        query = "$ANNEX_III_QUERY; resource type ${resource.type}",
        limit = 5,
        data = buildJsonObject { putJsonObject("resource") { putResource(resource) } },
        serializer = ClassificationReply.serializer(),
        isValid = { reply, evidence ->
            val type = ServiceTypes.parse(reply.serviceType)
            ReplyChecks.text(reply.reason) &&
                ReplyChecks.confidence(reply.confidence) &&
                type != null &&
                (type == ServiceType.Unknown || reply.citationIds.isNotEmpty()) &&
                ReplyChecks.citesOnly(reply.citationIds, evidence)
        },
        toAnswer = { reply, evidence ->
            when (val type = ServiceTypes.parse(reply.serviceType)!!) {
                ServiceType.Unknown -> AiAnswer.InsufficientEvidence(reply.reason.trim())
                else -> AiAnswer.Answer(type, reply.reason.trim(), ReplyChecks.selected(reply.citationIds, evidence), reply.confidence)
            }
        },
    )

    private companion object {
        const val ANNEX_III_QUERY =
            "CIR (EU) 2024/2956 Annex III type of ICT services: S17 Cloud services IaaS Infrastructure-as-a-Service; " +
                "S18 Cloud services PaaS Platform-as-a-Service; S19 Cloud services SaaS Software-as-a-Service"
    }
}

/** The resource fields the model may see; all of them are untrusted data. */
internal fun JsonObjectBuilder.putResource(resource: Resource) {
    put("type", resource.type)
    put("name", resource.name)
    put("provider", resource.provider)
    put("location", resource.location)
    putJsonObject("tags") { resource.tags.forEach { (key, value) -> put(key, value) } }
}
