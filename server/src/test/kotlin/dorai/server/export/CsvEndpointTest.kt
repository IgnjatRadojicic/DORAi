package dorai.server.export

import dorai.server.versionedClient
import dorai.server.module
import dorai.server.fixtures.fixtureServices
import dorai.server.fixtures.InMemoryRegisterRepository
import dorai.server.fixtures.FixtureData
import dorai.shared.api.ApiResult
import dorai.shared.api.DoraiApi
import io.ktor.client.request.get
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.*

class CsvEndpointTest {
    @Test
    fun csvEndpointAndSharedClientDownloadAnActualArchive() = testApplication {
        // The FinPay demo still has readiness errors, so only its draft package can be downloaded.
        application { module(fixtureServices()) }
        val response = versionedClient().get("/api/export/csv?draft=true")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.ContentType].orEmpty().startsWith("application/zip"))
        assertTrue(response.headers[HttpHeaders.ContentDisposition].orEmpty().contains("DRAFT_"))
        val bytes = response.body<ByteArray>()
        val names = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; names += entry.name }
        }
        // reportPackage.json, report.json, parameters.csv, FilingIndicators.csv and the 15 templates.
        assertEquals(19, names.size)
        DoraiApi("http://localhost", client).use { api ->
            val result = assertIs<ApiResult.Success<ByteArray>>(api.exportCsv(draft = true))
            assertEquals(listOf<Byte>(0x50, 0x4b), result.value.take(2))
        }
    }

    @Test
    fun validationErrorsReturnProblemDetailsWithoutAttachmentHeaders() = testApplication {
        val data = FixtureData.load()
        val invalid = data.register.copy(entity = data.register.entity.copy(name = ""))
        application { module(fixtureServices(data).copy(register = InMemoryRegisterRepository(invalid, data.resources, data.costs))) }
        val response = versionedClient().get("/api/export/csv")
        assertEquals(422, response.status.value)
        assertTrue(response.headers[HttpHeaders.ContentType].orEmpty().startsWith("application/problem+json"))
        assertNull(response.headers[HttpHeaders.ContentDisposition])
        assertTrue(response.bodyAsText().contains("Resolve register validation errors"))
    }

    @Test
    fun aDraftCanAlwaysBeExportedEvenWithErrors() = testApplication {
        val data = FixtureData.load()
        val invalid = data.register.copy(entity = data.register.entity.copy(name = ""))
        application { module(fixtureServices(data).copy(register = InMemoryRegisterRepository(invalid, data.resources, data.costs))) }
        val draft = versionedClient().get("/api/export/csv?draft=true")
        assertEquals(200, draft.status.value)
        assertTrue(draft.headers[HttpHeaders.ContentDisposition].orEmpty().contains("DRAFT_"))
    }
}
