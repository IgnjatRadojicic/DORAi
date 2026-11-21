package dorai.server.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DbConfigTest {

    private fun env(vararg pairs: Pair<String, String>) = mapOf(*pairs)::get

    @Test
    fun noUrlMeansFixtures() {
        assertNull(DbConfig.fromEnv(env()))
        assertNull(DbConfig.fromEnv(env("DORAI_DB_URL" to " ")))
    }

    @Test
    fun userDefaultsToDorai() {
        val config = DbConfig.fromEnv(env("DORAI_DB_URL" to "jdbc:postgresql://db:5432/dorai", "DORAI_DB_PASSWORD" to "pw"))
        assertEquals(DbConfig("jdbc:postgresql://db:5432/dorai", "dorai", "pw"), config)
    }

    @Test
    fun aUrlWithoutPasswordFailsAtStart() {
        assertFailsWith<IllegalStateException> { DbConfig.fromEnv(env("DORAI_DB_URL" to "jdbc:postgresql://db/dorai")) }
    }

    @Test
    fun thePasswordStaysOutOfLogs() = assertFalse("secret" in DbConfig("u", "dorai", "secret").toString())
}
