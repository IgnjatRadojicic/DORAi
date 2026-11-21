package dorai.server.export

import dorai.server.core.RegisterExporter
import dorai.server.core.ReportDrafter
import dorai.shared.model.Finding
import dorai.shared.model.Register
import dorai.shared.rules.RegisterValidator
import dorai.shared.rules.RulesRegisterValidator

/** Keeps both export formats on the same register validation and configured drafting service. */
class RegisterDocumentExporter(drafter: ReportDrafter? = null, validator: RegisterValidator = RulesRegisterValidator()) : RegisterExporter {
    private val csv = RegisterCsvExporter(validator)
    private val report = RegisterPdfReport(drafter, validator)

    override suspend fun csvZip(register: Register): ByteArray = csv.csvZip(register)
    override suspend fun pdf(register: Register, findings: List<Finding>): ByteArray = report.generate(register, findings)
}
