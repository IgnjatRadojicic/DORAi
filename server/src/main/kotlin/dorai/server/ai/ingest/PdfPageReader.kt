package dorai.server.ai.ingest

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

/** Reads the text of a PDF page by page, in content order. */
object PdfPageReader {
    fun read(pdf: ByteArray): List<PageText> =
        Loader.loadPDF(pdf).use { document ->
            val stripper = PDFTextStripper()
            (1..document.numberOfPages).map { number ->
                stripper.startPage = number
                stripper.endPage = number
                PageText(number, stripper.getText(document).lines())
            }
        }
}
