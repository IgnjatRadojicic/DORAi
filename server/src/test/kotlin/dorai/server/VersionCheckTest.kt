package dorai.server

import dorai.shared.Version
import dorai.shared.VersionProblem
import dorai.shared.model.DoraiJson
import dorai.shared.model.Health
import dorai.shared.model.Problem
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VersionCheckTest {

    private val current = Version.CURRENT

    private fun versions(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module() }
        block()
    }

    private suspend fun ApplicationTestBuilder.register(clientVersion: String?): HttpResponse =
        client.get("/api/register") { clientVersion?.let { header(Version.CLIENT_HEADER, it) } }

    private suspend fun HttpResponse.problem(): Problem = DoraiJson.decodeFromString(bodyAsText())

    @Test
    fun sameVersionPasses() = versions {
        assertEquals(HttpStatusCode.OK, register(current.toString()).status)
    }

    @Test
    fun anotherMinorOrPatchPasses() = versions {
        assertEquals(HttpStatusCode.OK, register("${current.major}.99.99").status)
        assertEquals(HttpStatusCode.OK, register("${current.major}.0.0").status)
    }

    @Test
    fun olderMajorMustUpdateTheApp() = versions {
        val response = register("${current.major - 1}.9.0")
        assertEquals(HttpStatusCode.UpgradeRequired, response.status)
        assertEquals(VersionProblem.CLIENT_OUTDATED, response.problem().type)
    }

    @Test
    fun newerMajorMustUpdateTheServer() = versions {
        val response = register("${current.major + 1}.0.0")
        assertEquals(HttpStatusCode.UpgradeRequired, response.status)
        assertEquals(VersionProblem.SERVER_OUTDATED, response.problem().type)
    }

    @Test
    fun missingOrMalformedVersionIsRefused() = versions {
        listOf(null, "", "latest", "${current.major}.0").forEach {
            val response = register(it)
            assertEquals(HttpStatusCode.BadRequest, response.status, "header '$it'")
            assertEquals(VersionProblem.CLIENT_VERSION_MISSING, response.problem().type)
        }
    }

    @Test
    fun healthIsAlwaysOpenAndSaysTheServerVersion() = versions {
        val response = client.get("/api/health") { header(Version.CLIENT_HEADER, "${current.major + 1}.0.0") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(current.toString(), DoraiJson.decodeFromString<Health>(response.bodyAsText()).version)
        assertEquals(HttpStatusCode.OK, client.get("/api/health").status)
    }

    @Test
    fun everyResponseCarriesTheServerVersion() = versions {
        assertEquals(current.toString(), client.get("/api/health").headers[Version.SERVER_HEADER])
        assertEquals(current.toString(), register(null).headers[Version.SERVER_HEADER])
        assertEquals(current.toString(), register(current.toString()).headers[Version.SERVER_HEADER])
    }

    @Test
    fun theProblemSaysWhoMustUpdate() {
        val server = Version(2, 3, 0)
        assertNull(versionProblem("2.0.5", server))
        assertEquals("Update DORAi", versionProblem("1.9.0", server)?.title)
        assertEquals("Server needs an update", versionProblem("3.0.0", server)?.title)
    }
}
