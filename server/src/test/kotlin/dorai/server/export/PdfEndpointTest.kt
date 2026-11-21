package dorai.server.export

import dorai.server.versionedClient
import dorai.server.ApiProblem
import dorai.server.core.AiAnswer
import dorai.server.core.DraftText
import dorai.server.core.FindingNote
import dorai.server.core.RegisterExporter
import dorai.server.core.ReportDrafter
import dorai.server.fixtures.FixtureData
import dorai.server.fixtures.InMemoryRegisterRepository
import dorai.server.fixtures.fixtureServices
import dorai.server.module
import dorai.shared.api.ApiResult
import dorai.shared.api.DoraiApi
import dorai.shared.model.Citation
import dorai.shared.model.Finding
import dorai.shared.model.Register
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.util.concurrent.atomic.AtomicInteger
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.test.*

class PdfEndpointTest {
    @Test
    fun realEndpointAndSharedClientReturnReadablePdfAttachments() = testApplication {
        application { module(fixtureServices()) }
        val response = versionedClient().get("/api/export/pdf")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.ContentType].orEmpty().startsWith("application/pdf"))
        assertTrue(response.headers[HttpHeaders.ContentDisposition].orEmpty().contains("dorai-report.pdf"))
        Loader.loadPDF(response.body<ByteArray>()).use { pdf ->
            assertTrue(pdf.numberOfPages >= 3)
            assertContains(PDFTextStripper().getText(pdf), "critical-function")
        }
        DoraiApi("http://localhost", client).use { api ->
            val bytes = assertIs<ApiResult.Success<ByteArray>>(api.exportPdf()).value
            Loader.loadPDF(bytes).use { assertContains(PDFTextStripper().getText(it), "ICT register review") }
        }
    }

    @Test
    fun invalidRegisterCanBeDownloadedForReviewWhileCsvRemainsBlocked() = testApplication {
        val data = FixtureData.load()
        val register = data.register.copy(entity = data.register.entity.copy(name = ""))
        application { module(fixtureServices(data).copy(register = InMemoryRegisterRepository(register, data.resources, data.costs))) }
        assertEquals(422, versionedClient().get("/api/export/csv").status.value)
        val pdf = versionedClient().get("/api/export/pdf")
        assertEquals(200, pdf.status.value)
        Loader.loadPDF(pdf.body<ByteArray>()).use {
            assertContains(PDFTextStripper().getText(it), "Basic validation: action required")
        }
    }

    @Test
    fun configuredDrafterReceivesTheSameSnapshotAndRuleFindings() = testApplication {
        val calls = AtomicInteger()
        val drafter = object : ReportDrafter {
            override suspend fun draft(register: Register, findings: List<Finding>): AiAnswer<DraftText> {
                calls.incrementAndGet()
                assertEquals("FinPay Payments B.V.", register.entity.name)
                assertEquals(4, findings.size)
                return AiAnswer.Answer(DraftText("Review the recorded third-party arrangements.", findings.map {
                    FindingNote(it.ruleId, it.resourceAddress, "Review this rule finding.")
                }), "Grounded", listOf(Citation("report-reference", "DORA", "Article 28", null,
                    "Financial entities shall manage ICT third-party risk.", 0.9)))
            }
        }
        val services = fixtureServices().let { it.copy(reportDrafter = drafter, exporter = RegisterDocumentExporter(drafter, it.validator)) }
        application { module(services) }
        val response = versionedClient().get("/api/export/pdf")
        assertEquals(200, response.status.value)
        assertEquals(1, calls.get())
        Loader.loadPDF(response.body<ByteArray>()).use {
            val text = PDFTextStripper().getText(it)
            assertContains(text, "Review the recorded third-party arrangements.")
            assertContains(text, "report-reference")
            assertContains(text, "critical-function")
        }
    }

    @Test
    fun generationFailureHasProblemDetailsAndNoAttachmentHeader() = testApplication {
        val services = fixtureServices()
        val failing = object : RegisterExporter by checkNotNull(services.exporter) {
            override suspend fun pdf(register: Register, findings: List<Finding>): ByteArray =
                throw ApiProblem(HttpStatusCode.ServiceUnavailable, "Report unavailable", "Please retry.")
        }
        application { module(services.copy(exporter = failing)) }
        val response = versionedClient().get("/api/export/pdf")
        assertEquals(503, response.status.value)
        assertTrue(response.headers[HttpHeaders.ContentType].orEmpty().startsWith("application/problem+json"))
        assertNull(response.headers[HttpHeaders.ContentDisposition])
    }
}
