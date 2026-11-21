package dorai.server.export

import dorai.server.core.RegisterExporter
import dorai.server.core.ReportDrafter
import dorai.shared.model.Finding
import dorai.shared.model.Register
import dorai.shared.model.Resource
import dorai.shared.roi.RoiTables
import dorai.shared.roi.RoiView
import dorai.shared.rules.RegisterValidator
import dorai.shared.rules.RulesRegisterValidator
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

/**
 * The RoI package for the supervisor and the PDF report for the firm, both from the same [RoiTables].
 * The PDF keeps its AI summary from [drafter] (null: no summary) and adds the 15 templates.
 */
class RoiExporter(
    drafter: ReportDrafter? = null,
    validator: RegisterValidator = RulesRegisterValidator(),
    private val clock: Clock = Clock.System,
) : RegisterExporter {
    private val report = RegisterPdfReport(drafter, validator, clock)

    override suspend fun reportPackage(register: Register, resources: List<Resource>, referenceDate: LocalDate, draft: Boolean): RoiPackage.Written {
        val report = RoiTables.build(register, resources, referenceDate)
        val currency = register.contracts.firstNotNullOfOrNull { it.currency } ?: "EUR"
        val createdAt = clock.now().toString().replace(Regex("[^0-9]"), "").take(17)
        return RoiPackage.write(report, register.entity.lei, register.entity.country, currency, createdAt, draft)
    }

    override suspend fun pdf(register: Register, resources: List<Resource>, referenceDate: LocalDate, findings: List<Finding>): ByteArray =
        report.generate(register, findings, RoiView.of(register, resources, referenceDate))
}
