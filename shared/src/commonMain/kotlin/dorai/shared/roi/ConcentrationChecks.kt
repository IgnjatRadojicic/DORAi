package dorai.shared.roi

import dorai.shared.model.ProvenanceLabel
import dorai.shared.model.Register
import dorai.shared.model.ValidationIssue
import dorai.shared.model.ValidationSeverity

/**
 * DORA Art. 29 (concentration) and Art. 28(8) (exit) on the register's own values. Deterministic and
 * warning-only: they point the reviewer at a risk to assess, they do not decide it.
 */
object ConcentrationChecks {

    fun check(report: RoiReport, register: Register): List<ValidationIssue> = buildList {
        val providers = report.table("B_05.01").rows.associateBy { it.key }
        val names = register.providers.associate { it.id to it.name }
        fun group(providerId: String) = providers[providerId]?.let { it.confirmed("B_05.01.0110") ?: it.confirmed("B_05.01.0010") } ?: providerId

        val critical = register.functions.filter(ResilienceChecks::isCritical).map { it.id }.toSet()
        val uses = register.contracts.flatMap { c ->
            c.services.mapNotNull { s -> s.functionId.value?.takeIf { it in critical }?.let { it to c.providerId } }
        }
        uses.groupBy { group(it.second) }.forEach { (parent, pairs) ->
            val members = pairs.map { it.second }.distinct()
            val functions = pairs.map { it.first }.distinct().sorted()
            if (members.size >= 2) {
                add(warn(
                    "art29-same-group", "B_05.01", "B_05.01.0110",
                    "${members.joinToString { names[it] ?: it }} belong to one group (ultimate parent $parent) and together support " +
                        "critical or important functions ${functions.joinToString()}.",
                    parent,
                ))
            }
            if (critical.size >= 2 && functions.size * 2 > critical.size) {
                add(warn(
                    "art29-dominant-group", "B_05.01", "B_05.01.0110",
                    "One group (${members.joinToString { names[it] ?: it }}) supports ${functions.size} of ${critical.size} critical or important functions.",
                    parent,
                ))
            }
        }

        report.table("B_07.01").rows.forEach { r ->
            if (r.confirmed("B_07.01.0080") == "false") {
                add(warn("art28-no-exit-plan", "B_07.01", "B_07.01.0080", "Contract ${r.value("B_07.01.0010")} supports a critical function and has no exit plan (Art. 28(8)).", r.key))
            }
            val hard = r.confirmed("B_07.01.0050") in setOf("1", "2")
            if (hard && r.confirmed("B_07.01.0110") != "1") {
                add(warn(
                    "art28-no-alternative", "B_07.01", "B_07.01.0110",
                    "Contract ${r.value("B_07.01.0010")} is hard to substitute and no alternative provider is identified.", r.key,
                ))
            }
        }
    }

    private fun RoiRowOut.confirmed(code: String) = cells[code]?.takeIf { it.label != ProvenanceLabel.AiSuggested }?.value

    private fun warn(code: String, template: String, field: String, message: String, id: String) =
        ValidationIssue(ValidationSeverity.Warning, code, template, field, message, id)
}
