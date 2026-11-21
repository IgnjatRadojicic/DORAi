package dorai.shared.roi

import dorai.shared.model.Register
import dorai.shared.model.Resource
import dorai.shared.model.ValidationIssue
import dorai.shared.model.ValidationSeverity

/**
 * DORA Art. 9 and 10 against the scan: a critical or important function running on a resource with open
 * high or critical findings from the cloud's own security tool. The tool reports; this rule decides that
 * the reviewer should look, as a warning. It never changes the register.
 */
object SecurityChecks {
    private val SERIOUS = setOf("HIGH", "CRITICAL")

    fun check(register: Register, resources: List<Resource>): List<ValidationIssue> {
        val critical = register.functions.filter(ResilienceChecks::isCritical).associateBy { it.id }
        return resources.mapNotNull { r ->
            val f = r.functionId?.let(critical::get) ?: return@mapNotNull null
            val serious = r.alerts.filter { it.severity in SERIOUS }
            if (serious.isEmpty()) return@mapNotNull null
            val sources = serious.map { it.source }.distinct().joinToString(" and ")
            ValidationIssue(
                ValidationSeverity.Warning, "ict-open-security-findings", "B_06.01", "B_06.01.0010",
                "${f.name} (${f.id}) runs on ${r.name}, which has ${serious.size} open high or critical findings in $sources: " +
                    serious.take(3).joinToString("; ") { it.title } + (if (serious.size > 3) "; and more" else "") + ".",
                r.id,
            )
        }
    }
}
