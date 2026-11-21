package dorai.server.ai.chat

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Offline stand-in for the chat model, the default provider. It answers from the request data with
 * fixed keyword rules and cites the supplied evidence by id, so the whole flow (prompt, parse,
 * citation checks, review) runs without a key or network. Every reason says it came from the fake.
 */
class FakeChatModel : ChatModel {
    override val modelId = "fake/keyword-rules"

    override suspend fun <T> ask(request: ChatRequest, serializer: KSerializer<T>): T {
        val data = request.data
        val evidence = data["evidence"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
        val reply = when (request.task) {
            AiTask.ClassifyServiceType -> classify(data, evidence)
            AiTask.SuggestFunction -> suggestFunction(data, evidence)
            AiTask.ExplainFinding -> explain(data, evidence)
            AiTask.DraftReport -> draft(data, evidence)
        }
        return try {
            json.decodeFromJsonElement(serializer, reply)
        } catch (e: SerializationException) {
            throw InvalidReplyException("The fake reply did not match the expected shape", e)
        }
    }

    private fun classify(data: JsonObject, evidence: List<String>): JsonObject {
        val type = data.text("resource", "type").lowercase()
        val rule = typeRules.firstOrNull { rule -> rule.keywords.any { it in type } }
            ?: return reply("serviceType" to "Unknown", "reason" to "no keyword rule matches '$type'.", confidence = 0.1, cited = emptyList())
        val cited = evidence.filter { it.endsWith("/${rule.serviceType}") }
            .ifEmpty { evidence.filter { "AnnexIII" in it } }
            .ifEmpty { evidence }
            .take(1)
        return reply("serviceType" to rule.serviceType, "reason" to "'$type' looks like a ${rule.label}.", confidence = 0.6, cited = cited)
    }

    private fun suggestFunction(data: JsonObject, evidence: List<String>): JsonObject {
        val resource = data["resource"]?.jsonObject
        val functions = data["functions"]?.jsonArray.orEmpty().map { it.jsonObject.text("id") to it.jsonObject.text("name") }
        val tagged = resource?.get("tags")?.jsonObject?.get(FUNCTION_TAG)?.jsonPrimitive?.contentOrNull
        val haystack = "${data.text("resource", "name")} ${data.text("resource", "type")}".lowercase()

        functions.firstOrNull { it.first == tagged }?.let { (id, name) ->
            return reply("functionId" to id, "reason" to "the $FUNCTION_TAG tag names '$name'.", confidence = 0.7, cited = evidence.take(1))
        }
        functions.firstOrNull { (_, name) -> stems(name).any { it in haystack } }?.let { (id, name) ->
            return reply("functionId" to id, "reason" to "the resource name matches '$name'.", confidence = 0.5, cited = evidence.take(1))
        }
        return reply("functionId" to "unknown", "reason" to "no function matches the resource.", confidence = 0.1, cited = emptyList())
    }

    private fun explain(data: JsonObject, evidence: List<String>) = buildJsonObject {
        val templates = data["finding"]?.jsonObject?.get("templates")?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
        val where = if (templates.isEmpty()) "the register of information" else templates.joinToString(", ")
        put("explanation", PREFIX + "'${data.text("finding", "title")}' changes what must be reported in $where.")
        put("citationIds", ids(evidence.take(1)))
    }

    private fun draft(data: JsonObject, evidence: List<String>) = buildJsonObject {
        val findings = data["findings"]?.jsonArray.orEmpty().map { it.jsonObject }
        val high = findings.count { it.text("severity") == "High" }
        put("summary", PREFIX + "register of ${data.text("register", "entity")} with ${findings.size} findings, $high of them high severity.")
        put(
            "findings",
            JsonArray(
                findings.map { finding ->
                    buildJsonObject {
                        put("ruleId", finding.text("ruleId"))
                        put("resourceAddress", finding.text("resourceAddress"))
                        put("text", PREFIX + "'${finding.text("title")}' (${finding.text("severity")}) needs a register update.")
                    }
                },
            ),
        )
        put("citationIds", ids(evidence.take(1)))
    }

    private fun reply(value: Pair<String, String>, reason: Pair<String, String>, confidence: Double, cited: List<String>) =
        buildJsonObject {
            put(value.first, value.second)
            put(reason.first, PREFIX + reason.second)
            put("confidence", confidence)
            put("citationIds", ids(cited))
        }

    private fun ids(values: List<String>) = JsonArray(values.map(::JsonPrimitive))

    private fun JsonObject.text(vararg path: String): String {
        var node: JsonElement? = this
        for (key in path) node = (node as? JsonObject)?.get(key)
        return (node as? JsonPrimitive)?.contentOrNull.orEmpty()
    }

    /** First five letters of each longer word, so "payments-api" matches "Card payment processing". */
    private fun stems(name: String) =
        name.lowercase().split(' ', '-', '_', '/', ',', '&').filter { it.length >= 5 }.map { it.take(5) }

    private class TypeRule(val serviceType: String, val label: String, val keywords: List<String>)

    private companion object {
        const val PREFIX = "Fake model (no LLM): "
        const val FUNCTION_TAG = "dora-function"
        val json = Json { ignoreUnknownKeys = true }

        /** Platform services first, so "db_instance" is a database and not a virtual machine. */
        val typeRules = listOf(
            TypeRule("S18", "managed database (PaaS)", listOf("postgres", "sql", "cosmos", "database", "dynamodb", "rds", "redis", "cache")),
            TypeRule("S18", "managed application platform (PaaS)", listOf("microsoft.web/", "app_service", "function", "lambda", "kubernetes", "container", "aks", "eks")),
            TypeRule(
                "S18",
                "managed platform service (PaaS)",
                listOf("cognitiveservices", "openai", "keyvault", "key_vault", "servicebus", "eventhub", "sqs", "sns", "operationalinsights", "log_analytics", "search"),
            ),
            TypeRule(
                "S17",
                "infrastructure service (IaaS)",
                listOf("virtualmachine", "virtual_machine", "compute", "ec2", "storage", "s3", "disk", "network", "vnet", "vpc", "subnet", "loadbalancer"),
            ),
        )
    }
}
