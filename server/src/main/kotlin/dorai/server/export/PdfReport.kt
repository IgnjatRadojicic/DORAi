package dorai.server.export

import org.openpdf.text.Document
import org.openpdf.text.Element
import org.openpdf.text.Font
import org.openpdf.text.FontFactory
import org.openpdf.text.PageSize
import org.openpdf.text.Paragraph
import org.openpdf.text.Phrase
import org.openpdf.text.pdf.PdfPCell
import org.openpdf.text.pdf.PdfPTable
import org.openpdf.text.pdf.PdfWriter
import dorai.shared.model.Finding
import dorai.shared.model.Register
import dorai.shared.model.ValidationSeverity
import dorai.shared.roi.RoiView
import java.io.ByteArrayOutputStream

/**
 * The register report for people: the entity, how complete the RoI is per template, what is still
 * missing, and the current findings. The supervisor gets the CSV package; this is for the firm.
 */
object PdfReport {
    private val title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f)
    private val heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f)
    private val body = FontFactory.getFont(FontFactory.HELVETICA, 9f)
    private val bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f)

    fun write(register: Register, view: RoiView, findings: List<Finding>): ByteArray {
        val out = ByteArrayOutputStream()
        val doc = Document(PageSize.A4, 40f, 40f, 40f, 40f)
        PdfWriter.getInstance(doc, out)
        doc.open()
        doc.add(Paragraph("Register of Information: ${register.entity.name}", title))
        doc.add(Paragraph("LEI ${register.entity.lei}, ${register.entity.country}. Reference date ${view.referenceDate}.", body))
        doc.add(Paragraph(
            if (view.ready) "Ready for export: no errors." else "Not ready: ${view.errorCount} errors and ${view.warningCount} warnings to resolve.",
            bold,
        ))

        section(doc, "Templates")
        table(doc, listOf("Template", "Rows", "Errors"), view.tables.map { (code, rows) ->
            listOf(code, rows.size.toString(), view.issues.count { it.template == code && it.severity == ValidationSeverity.Error }.toString())
        }, floatArrayOf(2f, 1f, 1f))

        val errors = view.issues.filter { it.severity == ValidationSeverity.Error }
        if (errors.isNotEmpty()) {
            section(doc, "Missing or invalid before export")
            table(doc, listOf("Column", "Row", "Issue"), errors.take(MAX_ROWS).map { listOf(it.field, it.entityId.orEmpty(), it.message) }, floatArrayOf(2f, 2f, 6f))
            if (errors.size > MAX_ROWS) doc.add(Paragraph("${errors.size - MAX_ROWS} more errors are listed in the app.", body))
        }

        section(doc, "Findings")
        if (findings.isEmpty()) doc.add(Paragraph("No findings.", body))
        else table(doc, listOf("Severity", "Rule", "Finding"), findings.map { listOf(it.severity.name, it.ruleId, it.title) }, floatArrayOf(1f, 2f, 6f))
        doc.close()
        return out.toByteArray()
    }

    private fun section(doc: Document, text: String) {
        doc.add(Paragraph(text, heading).apply { spacingBefore = 14f; spacingAfter = 6f })
    }

    private fun table(doc: Document, header: List<String>, rows: List<List<String>>, widths: FloatArray) {
        val t = PdfPTable(widths).apply { widthPercentage = 100f; headerRows = 1 }
        header.forEach { t.addCell(cell(it, bold)) }
        rows.forEach { r -> r.forEach { t.addCell(cell(it, body)) } }
        doc.add(t)
    }

    private fun cell(text: String, font: Font) = PdfPCell(Phrase(text, font)).apply {
        verticalAlignment = Element.ALIGN_TOP
        setPadding(3f)
    }

    private const val MAX_ROWS = 200
}
