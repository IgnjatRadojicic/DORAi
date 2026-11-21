package dorai.server.export

import dorai.server.ApiProblem
import dorai.server.core.RegisterExporter
import dorai.shared.model.DoraiJson
import dorai.shared.model.Finding
import dorai.shared.model.Register
import dorai.shared.rules.RegisterValidator
import dorai.shared.rules.RulesRegisterValidator
import io.ktor.http.HttpStatusCode
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter

/** CSV-only implementation. PDF remains unavailable until its implementation is supplied. */
class RegisterCsvExporter(private val validator: RegisterValidator = RulesRegisterValidator()) : RegisterExporter {
    override suspend fun csvZip(register: Register): ByteArray = withContext(Dispatchers.Default) {
        val context = currentCoroutineContext()
        context.ensureActive()
        val validation = validator.validate(register)
        if (!validation.isValid || validation.errorCount > 0) {
            throw ApiProblem(HttpStatusCode.UnprocessableEntity, "Register validation failed",
                "Resolve register validation errors before exporting.")
        }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output, Charsets.UTF_8).use { zip ->
            zip.setLevel(6)
            fun add(name: String, bytes: ByteArray) {
                context.ensureActive()
                // The exact ZIP epoch triggers a timezone-dependent extra timestamp in the JDK.
                zip.putNextEntry(ZipEntry(name).apply { timeLocal = LocalDateTime.of(1980, 1, 2, 0, 0) })
                zip.write(bytes)
                zip.closeEntry()
            }
            CsvTemplate.entries.forEach { template ->
                val buffer = StringWriter()
                val format = CSVFormat.RFC4180.builder().setHeader(*template.headers.toTypedArray()).setRecordSeparator("\r\n").get()
                CSVPrinter(buffer, format).use { csv ->
                    template.rows(register).forEach { row ->
                        context.ensureActive()
                        check(row.size == template.columns.size) { "Incorrect column count for ${template.code}" }
                        csv.printRecord(row.map(ExportCells::printable))
                    }
                }
                add(template.fileName, buffer.toString().toByteArray(Charsets.UTF_8))
            }
            add("README.txt", NOTES.toByteArray(Charsets.UTF_8))
            add("manifest.json", DoraiJson.encodeToString(manifest(register)).toByteArray(Charsets.UTF_8))
        }
        context.ensureActive()
        output.toByteArray()
    }

    override suspend fun pdf(register: Register, findings: List<Finding>): ByteArray =
        throw ApiProblem(HttpStatusCode.NotImplemented, "Export unavailable", "PDF export is not configured.")

    private companion object {
        const val NOTES = """DORAi register CSV package

Draft for review. This archive is not a submission-ready regulatory filing or xBRL-CSV package.
Five register templates are included. Unsupported and missing cells remain empty.
Unconfirmed AI suggestions are excluded. The manifest lists omitted fields and data gaps.
Amounts use decimal points, and provider totals are calculated only for complete, single-currency data.
Review the manifest and the register before using these files for reporting.
"""
    }
}
