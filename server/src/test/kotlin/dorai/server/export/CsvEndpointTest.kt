package dorai.server.export

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
        application { module(fixtureServices()) }
        val response = client.get("/api/export/csv")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.ContentType].orEmpty().startsWith("application/zip"))
        assertTrue(response.headers[HttpHeaders.ContentDisposition].orEmpty().contains("dorai-register.zip"))
        val bytes = response.body<ByteArray>()
        val names = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; names += entry.name }
        }
        assertEquals(7, names.size)
        DoraiApi("http://localhost", client).use { api ->
            val result = assertIs<ApiResult.Success<ByteArray>>(api.exportCsv())
            assertContentEquals(bytes, result.value)
            assertEquals(501, assertIs<ApiResult.Failure>(api.exportPdf()).problem.status)
        }
    }

    @Test
    fun validationErrorsReturnProblemDetailsWithoutAttachmentHeaders() = testApplication {
        val data = FixtureData.load()
        val invalid = data.register.copy(entity = data.register.entity.copy(name = ""))
        application { module(fixtureServices(data).copy(register = InMemoryRegisterRepository(invalid, data.resources, data.costs))) }
        val response = client.get("/api/export/csv")
        assertEquals(422, response.status.value)
        assertTrue(response.headers[HttpHeaders.ContentType].orEmpty().startsWith("application/problem+json"))
        assertNull(response.headers[HttpHeaders.ContentDisposition])
        assertTrue(response.bodyAsText().contains("Resolve register validation errors"))
    }

    @Test
    fun registerWithWarningsCanStillBeExported() = testApplication {
        val data = FixtureData.load()
        val first = data.register.providers[0]
        val register = data.register.copy(providers = listOf(first.copy(lei = first.lei.copy(value = null))) + data.register.providers.drop(1))
        application { module(fixtureServices(data).copy(register = InMemoryRegisterRepository(register, data.resources, data.costs))) }
        assertEquals(200, client.get("/api/export/csv").status.value)
    }
}
