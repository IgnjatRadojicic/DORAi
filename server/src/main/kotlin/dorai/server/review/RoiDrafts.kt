package dorai.server.review

import dorai.server.core.AiAnswer
import dorai.server.core.RoiValueDrafter
import dorai.shared.model.Register
import dorai.shared.model.Resource
import dorai.shared.model.Suggestion
import dorai.shared.model.SuggestionKind
import dorai.shared.model.SuggestionStatus
import dorai.shared.roi.RoiColumn
import dorai.shared.roi.RoiReadiness
import dorai.shared.roi.RoiSpec
import dorai.shared.roi.RoiTables
import kotlinx.datetime.LocalDate
import java.util.UUID
import kotlin.time.Instant

/**
 * The register values that take judgement rather than a scan: which of them are still empty while the
 * RoI needs them, and the AI drafts for those. Rules decide which cells to draft; the AI only proposes.
 */
object RoiDrafts {

    /** Columns the AI may draft, by template. */
    val DRAFTABLE = listOf("B_02.02.0170", "B_02.02.0180", "B_06.01.0020", "B_07.01.0050")

    /** One empty, required judgement cell: [itemId] is the register item the accepted value is written to. */
    data class Target(val itemId: String, val itemName: String, val column: RoiColumn, val facts: Map<String, String>)

    fun targets(register: Register, resources: List<Resource>, date: LocalDate, covered: Set<Pair<String, String>>): List<Target> {
        val report = RoiTables.build(register, resources, date)
        val functions = register.functions.associateBy { it.id }
        val providers = register.providers.associateBy { it.id }
        val contracts = register.contracts.associateBy { it.id }
        val serviceContract = register.contracts.flatMap { c -> c.services.map { it.id to c } }.toMap()

        return DRAFTABLE.flatMap { code ->
            val column = RoiSpec.column(code) ?: return@flatMap emptyList()
            val template = code.substringBeforeLast('.')
            report.table(template).rows.mapNotNull { row ->
                if (row.value(code) != null || !RoiReadiness.required(column, row)) return@mapNotNull null
                val target = when (template) {
                    "B_02.02" -> {
                        val serviceId = row.key.substringBefore('#')
                        val contract = serviceContract[serviceId] ?: return@mapNotNull null
                        val function = functions[row.value("B_02.02.0050")]
                        // Named by the service, not the contract: one contract often has several services of one type.
                        val type = row.value("B_02.02.0060")
                        val typeName = type?.let { t -> listOfNotNull(t, RoiSpec.column("B_02.02.0060")?.label(t)).joinToString(" ") }
                        Target(serviceId, typeName?.let { "$serviceId ($it)" } ?: serviceId, column, buildMap {
                            put("serviceType", row.value("B_02.02.0060").orEmpty())
                            put("function", function?.name.orEmpty())
                            put("critical", row.facts.critical.toString())
                            put("processesData", row.facts.processesData.toString())
                            put("dataStoredIn", row.value("B_02.02.0150").orEmpty())
                            put("provider", providers[contract.providerId]?.name.orEmpty())
                        })
                    }
                    "B_06.01" -> functions[row.key]?.let { f ->
                        Target(f.id, f.name, column, mapOf("function" to f.name, "critical" to f.isCritical.toString(), "entityType" to register.entity.roi["B_01.02.0040"]?.value.orEmpty()))
                    }
                    "B_07.01" -> {
                        val contract = contracts[row.key.substringBefore('|')] ?: return@mapNotNull null
                        Target(contract.id, contract.id, column, mapOf(
                            "serviceType" to row.value("B_07.01.0040").orEmpty(),
                            "provider" to providers[contract.providerId]?.name.orEmpty(),
                            "critical" to "true",
                        ))
                    }
                    else -> null
                }
                target?.takeIf { (it.itemId to code) !in covered }
            }.distinctBy { it.itemId to it.column.code }
        }
    }

    suspend fun draft(drafter: RoiValueDrafter, targets: List<Target>, now: Instant): List<Suggestion> = targets.mapNotNull { t ->
        val answer = drafter.draftValue(t.column, t.itemName, t.facts) as? AiAnswer.Answer<String> ?: return@mapNotNull null
        Suggestion(
            id = UUID.randomUUID().toString(),
            resourceId = t.itemId,
            resourceName = t.itemName,
            kind = SuggestionKind.RoiValue,
            value = answer.value,
            reason = answer.reason,
            citations = answer.citations,
            confidence = answer.confidence,
            status = SuggestionStatus.Pending,
            createdAt = now,
            column = t.column.code,
        )
    }
}
