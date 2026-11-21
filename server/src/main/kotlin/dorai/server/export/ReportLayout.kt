package dorai.server.export

import java.awt.Color
import java.io.ByteArrayOutputStream
import org.openpdf.text.Document
import org.openpdf.text.Element
import org.openpdf.text.Font
import org.openpdf.text.PageSize
import org.openpdf.text.Paragraph
import org.openpdf.text.Phrase
import org.openpdf.text.Rectangle
import org.openpdf.text.pdf.BaseFont
import org.openpdf.text.pdf.ColumnText
import org.openpdf.text.pdf.PdfPCell
import org.openpdf.text.pdf.PdfPTable
import org.openpdf.text.pdf.PdfPageEventHelper
import org.openpdf.text.pdf.PdfWriter

/** Per-document layout state: embedded fonts and repeating page furniture, without system fonts. */
internal class ReportLayout(private val output: ByteArrayOutputStream) : AutoCloseable {
    private val regular = font("host-400.ttf")
    private val semibold = font("host-600.ttf")
    private val ink = Color(0x23, 0x25, 0x2D)
    private val muted = Color(0x65, 0x69, 0x78)
    private val rule = Color(0xDE, 0xE2, 0xEA)
    private val accent = Color(0x35, 0x64, 0xD8)
    private val document = Document(PageSize.A4, 44f, 44f, 64f, 52f)
    private val writer = PdfWriter.getInstance(document, output)

    init {
        document.addTitle("DORAi | ICT register review")
        document.addAuthor("DORAi")
        document.addSubject("Draft register review and rule findings")
        writer.pageEvent = object : PdfPageEventHelper() {
            override fun onEndPage(writer: PdfWriter, document: Document) {
                val canvas = writer.directContent
                canvas.saveState()
                canvas.setColorStroke(rule)
                canvas.setLineWidth(0.5f)
                canvas.moveTo(44f, 798f)
                canvas.lineTo(551f, 798f)
                canvas.moveTo(44f, 39f)
                canvas.lineTo(551f, 39f)
                canvas.stroke()
                ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT, phrase("DORAi", 11f, true), 44f, 813f, 0f)
                ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT, phrase("REGISTER REVIEW", 8f, color = muted), 551f, 813f, 0f)
                ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT, phrase("Draft for review", 8f, color = muted), 44f, 25f, 0f)
                ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT, phrase("Page " + writer.pageNumber, 8f, color = muted), 551f, 25f, 0f)
                canvas.restoreState()
            }
        }
        document.open()
    }

    fun title(kicker: String, title: String) {
        text(kicker.uppercase(), 8f, true, accent, 7f)
        text(title, 28f, true, ink, 14f)
    }

    fun heading(number: String, label: String) = text("$number  /  $label", 12f, true, ink, 9f, 18f)

    fun text(value: String, size: Float = 9f, bold: Boolean = false, color: Color = ink,
             after: Float = 7f, before: Float = 0f) {
        document.add(Paragraph(phrase(value.ifBlank { "Not recorded" }, size, bold, color)).apply {
            setLeading(size * 1.4f)
            spacingAfter = after
            spacingBefore = before
        })
    }

    fun note(value: String) = text(value, 8f, color = muted)
    fun page() { document.newPage() }

    fun table(headers: List<String>, widths: FloatArray, rows: List<List<String>>) {
        val table = PdfPTable(widths).apply {
            widthPercentage = 100f
            headerRows = 1
            setSpacingAfter(10f)
            setSplitRows(true)
            setSplitLate(false)
        }
        headers.forEach { table.addCell(cell(it, header = true)) }
        if (rows.isEmpty()) {
            table.addCell(cell("No entries recorded").apply { colspan = headers.size })
        } else rows.forEachIndexed { index, row ->
            check(row.size == headers.size)
            row.forEach { table.addCell(cell(it, alternate = index % 2 == 0)) }
        }
        document.add(table)
    }

    private fun cell(text: String, header: Boolean = false, alternate: Boolean = false) =
        PdfPCell(phrase(text.ifBlank { "Not recorded" }, 8f, header, if (header) muted else ink)).apply {
            border = Rectangle.BOTTOM
            borderColor = rule
            borderWidth = 0.5f
            setPadding(8f)
            verticalAlignment = Element.ALIGN_TOP
            if (header || alternate) backgroundColor = Color(0xF4, 0xF6, 0xFA)
        }

    private fun phrase(text: String, size: Float, bold: Boolean = false, color: Color = ink) =
        Phrase(text.filter { !it.isISOControl() || it == '\n' || it == '\t' }, Font(if (bold) semibold else regular, size, Font.NORMAL, color))

    override fun close() { document.close() }

    private fun font(name: String): BaseFont {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/export/fonts/$name")) { "Missing report font" }.use { it.readBytes() }
        return BaseFont.createFont(name, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, false, bytes, null)
    }
}
