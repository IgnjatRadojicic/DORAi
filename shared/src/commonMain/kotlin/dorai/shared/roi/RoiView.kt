package dorai.shared.roi

import dorai.shared.model.Register
import dorai.shared.model.Resource
import dorai.shared.model.ValidationIssue
import dorai.shared.model.ValidationSeverity
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * The RoI as the server sends it to the app and the CLI: every template's rows by template code, and what
 * is missing or wrong before the export. [ready] means no errors: the final package can be built.
 */
@Serializable
data class RoiView(
    val referenceDate: LocalDate,
    val ready: Boolean,
    val errorCount: Int,
    val warningCount: Int,
    val tables: Map<String, List<RoiRowOut>>,
    val issues: List<ValidationIssue>,
) {
    companion object {
        fun of(register: Register, resources: List<Resource>, referenceDate: LocalDate, vendors: List<VendorSpend> = emptyList()): RoiView {
            val report = RoiTables.build(register, resources, referenceDate)
            val issues = RoiReadiness.check(report, register) + ResilienceChecks.check(register, resources) +
                VendorChecks.check(register, vendors) + VendorChecks.checkApps(register, resources)
            val errors = issues.count { it.severity == ValidationSeverity.Error }
            return RoiView(
                referenceDate = referenceDate,
                ready = errors == 0,
                errorCount = errors,
                warningCount = issues.size - errors,
                tables = report.tables.associate { it.template.code to it.rows },
                issues = issues,
            )
        }
    }
}
