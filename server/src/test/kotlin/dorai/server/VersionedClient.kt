package dorai.server

import dorai.shared.Version
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.server.testing.ApplicationTestBuilder

/** A plain test client that says this build's version, as every DORAi client does; the server refuses calls without it. */
fun ApplicationTestBuilder.versionedClient(): HttpClient = createClient {
    defaultRequest { header(Version.CLIENT_HEADER, Version.CURRENT.toString()) }
}
