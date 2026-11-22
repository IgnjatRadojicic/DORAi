package dorai.server.db

import dorai.server.core.Services
import dorai.server.fixtures.FixtureData
import dorai.server.fixtures.fixtureServices
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("dorai.server.db")

/**
 * Loads the FinPay demo data when no register is stored yet. Returns whether it did.
 * Once a register exists (a real firm's or the demo's), nothing is touched.
 */
fun seedIfEmpty(db: Database, data: FixtureData): Boolean = transaction(db) {
    if (!registerIsEmpty()) return@transaction false
    writeRegister(data.register)
    writeScan(data.resources, data.costs)
    writeSuggestions(data.suggestions)
    true
}

/** Phase 2 wiring: register, scan and suggestions in PostgreSQL; collectors, costs and AI as in fixture mode. */
fun postgresServices(db: Database, data: FixtureData = FixtureData.load()): Services {
    if (seedIfEmpty(db, data)) log.info("Empty database: seeded the FinPay demo register.")
    return fixtureServices(data).copy(
        register = PostgresRegisterRepository(db),
        suggestions = PostgresSuggestionRepository(db),
        plans = PostgresPlanRepository(db),
    )
}
