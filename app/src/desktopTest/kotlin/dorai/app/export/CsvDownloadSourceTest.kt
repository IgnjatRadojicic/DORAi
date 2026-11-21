package dorai.app.export

import com.sun.net.httpserver.HttpServer
import dorai.app.components.FrontendFailure
import dorai.app.findings.PlanDocument
import dorai.app.validation.ApiRegisterValidation
import dorai.app.validation.IssueSeverity
import dorai.shared.api.DoraiApi
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class CsvDownloadSourceTest {
    @Test
    fun serverBytesReachTheFileSaverThroughTheSharedClient() = runBlocking {
        val bytes = archive()
        withBackend("/api/export/csv", 200, bytes, "application/zip") { api, calls ->
            val files = FilesFake()
            val source = CsvDownloadSource(api)
            val model = ExportViewModel(source, files)
            model.download(ExportFormat.CSV)
            assertContentEquals(bytes, files.bytes)
            assertEquals("register.zip", files.name)
            assertEquals("application/zip", files.mediaType)
            assertEquals("selected/register.zip", model.state.savedLocation)
            assertNull(model.state.error)
            assertFailsWith<FrontendFailure> { source.download(ExportFormat.PDF) }
            assertEquals(1, calls.get(), "PDF must not call an unimplemented endpoint")
        }
    }

    @Test
    fun validationFailureDoesNotOpenAFileDestination() = runBlocking {
        val problem = """{"title":"Register validation failed","status":422,"detail":"Invalid register"}"""
        withBackend("/api/export/csv", 422, problem.encodeToByteArray(), "application/problem+json") { api, _ ->
            val files = FilesFake()
            val model = ExportViewModel(CsvDownloadSource(api), files)
            model.download(ExportFormat.CSV)
            assertEquals("Resolve register validation errors before exporting.", model.state.error)
            assertNull(model.state.savedLocation)
            assertNull(files.bytes)
        }
    }

    @Test
    fun serverValidationUsesTheActualRegisterReport() = runBlocking {
        val report = """{"isValid":false,"errorCount":1,"warningCount":0,"issues":[{"severity":"Error","code":"mandatory-field","template":"B_01.01","field":"Name","message":"Name is required.","entityId":"E1"}]}"""
        withBackend("/api/validation", 200, report.encodeToByteArray(), "application/json") { api, _ ->
            val validation = ApiRegisterValidation(api).validateRegister()
            assertFalse(validation.isValid)
            assertEquals(1, validation.errorCount)
            assertEquals(IssueSeverity.ERROR, validation.issues.single().severity)
            assertEquals("Name is required.", validation.issues.single().message)
            assertEquals("E1", validation.issues.single().entityId)
        }
    }

    private class FilesFake : WorkspaceFiles {
        var bytes: ByteArray? = null
        var name: String? = null
        var mediaType: String? = null
        override suspend fun openPlan(): PlanDocument? = null
        override suspend fun save(name: String, mediaType: String, bytes: ByteArray): SaveOutcome {
            this.name = name
            this.mediaType = mediaType
            this.bytes = bytes
            return SaveOutcome.Saved("selected/$name")
        }
    }

    private suspend fun withBackend(path: String, status: Int, bytes: ByteArray, type: String, block: suspend (DoraiApi, AtomicInteger) -> Unit) {
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext(path) { exchange ->
            calls.incrementAndGet()
            exchange.responseHeaders.set("Content-Type", type)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { DoraiApi("http://127.0.0.1:${server.address.port}").use { block(it, calls) } }
        finally { server.stop(0) }
    }

    private fun archive(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("B_01.01.csv"))
            zip.write("id,name\r\n1,FinPay\r\n".encodeToByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}
