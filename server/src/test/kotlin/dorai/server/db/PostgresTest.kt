package dorai.server.db

import dorai.server.core.SuggestionRepository.Decision
import dorai.server.fixtures.FixtureData
import dorai.server.module
import dorai.shared.api.ApiResult
import dorai.shared.api.DoraiApi
import dorai.shared.model.SuggestionStatus
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.testcontainers.DockerClientFactory
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.Instant

/** One PostgreSQL with pgvector for all tests, the same image as deploy/compose.yaml. Skipped without Docker. */
object TestPostgres {
    private val db: Database by lazy {
        val container = PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:0.8.7-pg16").asCompatibleSubstituteFor("postgres"))
        container.start()
        Db.open(DbConfig(container.jdbcUrl, container.username, container.password))
    }

    /** The database with every table emptied, as after a fresh migration. */
    fun fresh(): Database {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable, "Docker is not available")
        transaction(db) {
            exec(
                "TRUNCATE financial_entities, providers, business_functions, contracts, contract_services, " +
                    "resources, cost_entries, suggestions, users, refresh_tokens, invites RESTART IDENTITY",
            )
        }
        return db
    }
}

class PostgresTest {

    private val data = FixtureData.load()

    private fun seeded(): Database = TestPostgres.fresh().also { assertTrue(seedIfEmpty(it, data)) }

    @Test
    fun migrationEnablesPgvector() {
        val db = TestPostgres.fresh()
        val installed = transaction(db) { exec("SELECT 1 FROM pg_extension WHERE extname = 'vector'") { it.next() } }
        assertEquals(true, installed)
    }

    @Test
    fun theFinPayDataRoundTrips() = runTest {
        val db = seeded()
        val register = PostgresRegisterRepository(db)
        // Field by field, provenance and citations included.
        assertEquals(data.register, register.register())
        assertEquals(data.resources, register.resources())
        assertEquals(data.costs, register.costs())
        assertEquals(data.suggestions, PostgresSuggestionRepository(db).all())
    }

    @Test
    fun seedLeavesAStoredRegisterAlone() = runTest {
        val db = seeded()
        val repo = PostgresRegisterRepository(db)
        repo.saveScan(data.resources.take(1), emptyList())
        assertFalse(seedIfEmpty(db, data))
        assertEquals(data.resources.take(1), repo.resources())
    }

    @Test
    fun saveScanReplacesTheStoredScan() = runTest {
        val repo = PostgresRegisterRepository(seeded())
        val resources = listOf(data.resources[1], data.resources[0].copy(name = "renamed", tags = mapOf("a" to "b")))
        val costs = data.costs.take(2).reversed()
        repo.saveScan(resources, costs)
        assertEquals(resources, repo.resources())
        assertEquals(costs, repo.costs())
    }

    @Test
    fun decidingASuggestion() = runTest {
        val now = Instant.parse("2026-11-21T10:00:00Z")
        val repo = PostgresSuggestionRepository(seeded(), clock = object : Clock { override fun now() = now })
        val first = data.suggestions.first { it.status == SuggestionStatus.Pending }

        val done = assertIs<Decision.Done>(repo.decide(first.id, SuggestionStatus.Accepted)).suggestion
        assertEquals(first.copy(status = SuggestionStatus.Accepted, decidedAt = now), done)
        assertEquals(done, assertIs<Decision.AlreadyDecided>(repo.decide(first.id, SuggestionStatus.Rejected)).suggestion)
        assertEquals(Decision.NotFound, repo.decide("missing", SuggestionStatus.Accepted))

        val pending = data.suggestions.filter { it.status == SuggestionStatus.Pending && it.id != first.id }
        assertEquals(pending, repo.pending())
        assertEquals(pending.filter { it.resourceId == pending[0].resourceId }, repo.pending(pending[0].resourceId))
    }

    @Test
    fun addReplacesBySameIdAndKeepsTheOrder() = runTest {
        val repo = PostgresSuggestionRepository(seeded())
        val changed = data.suggestions[0].copy(reason = "Changed.")
        val added = data.suggestions[1].copy(id = "new-one")
        repo.add(listOf(changed, added))
        assertEquals(listOf(changed) + data.suggestions.drop(1) + added, repo.all())
    }

    @Test
    fun theApiRunsOnPostgres() {
        val db = TestPostgres.fresh()
        testApplication {
            application { module(postgresServices(db, data)) }
            DoraiApi("http://localhost", client).use { api ->
                assertEquals(data.register, api.register().ok())
                assertTrue(api.validation().ok().isValid)
                assertEquals(4, api.check().ok().size)

                val id = data.suggestions.first { it.status == SuggestionStatus.Pending }.id
                api.acceptSuggestion(id).ok()
                val stored = PostgresSuggestionRepository(db).all().single { it.id == id }
                assertEquals(SuggestionStatus.Accepted, stored.status)
            }
        }
    }

    private fun <T> ApiResult<T>.ok(): T = when (this) {
        is ApiResult.Success -> value
        is ApiResult.Failure -> fail("Expected success, got $problem")
    }
}
