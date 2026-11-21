package dorai.shared.api

import dorai.shared.Version
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DoraiApiTest {

    private fun api(status: HttpStatusCode, body: String, contentType: String = "application/json") =
        DoraiApi(
            "http://server:8080/",
            HttpClient(MockEngine { request ->
                assertEquals("/api/health", request.url.encodedPath)
                respond(body, status, headersOf(HttpHeaders.ContentType, contentType))
            }),
        )

    @Test
    fun successDecodesTheBody() = runTest {
        val result = api(HttpStatusCode.OK, """{"status":"ok","app":"DORAi"}""").health()
        assertEquals("ok", assertIs<ApiResult.Success<*>>(result).let { result.getOrNull()?.status })
    }

    @Test
    fun problemDetailsBecomeAFailure() = runTest {
        val body = """{"type":"about:blank","title":"Suggestion not found","status":404,"detail":"No suggestion"}"""
        val result = api(HttpStatusCode.NotFound, body, "application/problem+json").health()
        val failure = assertIs<ApiResult.Failure>(result)
        assertEquals("Suggestion not found", failure.problem.title)
        assertEquals(404, failure.problem.status)
    }

    @Test
    fun nonJsonErrorStillGivesAProblem() = runTest {
        val failure = assertIs<ApiResult.Failure>(api(HttpStatusCode.BadGateway, "upstream down", "text/plain").health())
        assertEquals(502, failure.problem.status)
        assertEquals("upstream down", failure.problem.detail)
    }

    @Test
    fun networkErrorIsAFailureWithStatusZero() = runTest {
        val api = DoraiApi("http://server", HttpClient(MockEngine { error("connection refused") }))
        val failure = assertIs<ApiResult.Failure>(api.health())
        assertEquals(0, failure.problem.status)
    }

    @Test
    fun everyRequestSaysTheClientVersionAndTheServerVersionIsRead() = runTest {
        val api = DoraiApi(
            "http://server:8080/",
            HttpClient(MockEngine { request ->
                assertEquals(Version.CURRENT.toString(), request.headers[Version.CLIENT_HEADER])
                respond(
                    """{"status":"ok","app":"DORAi","version":"1.9.2"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType to listOf("application/json"), Version.SERVER_HEADER to listOf("1.9.2")),
                )
            }),
        )
        assertNull(api.serverVersion)
        val health = api.health().getOrNull()
        assertEquals("1.9.2", health?.version)
        assertEquals(Version(1, 9, 2), api.serverVersion)
    }
}
