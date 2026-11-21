package dorai.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import javax.sql.DataSource

/** Where the database is. Read from the environment that deploy/compose.yaml and .env provide. */
data class DbConfig(val url: String, val user: String, val password: String) {
    override fun toString() = "DbConfig(url=$url, user=$user)"

    companion object {
        /** Null when DORAI_DB_URL is not set: the server then runs on fixtures. */
        fun fromEnv(env: (String) -> String? = System::getenv): DbConfig? {
            val url = env("DORAI_DB_URL")?.takeIf { it.isNotBlank() } ?: return null
            val password = env("DORAI_DB_PASSWORD") ?: error("DORAI_DB_URL is set but DORAI_DB_PASSWORD is not.")
            return DbConfig(url, env("DORAI_DB_USER")?.takeIf { it.isNotBlank() } ?: "dorai", password)
        }
    }
}

object Db {
    /** Opens a pool, brings the schema up to date with Flyway and returns the Exposed handle. */
    fun open(config: DbConfig): Database {
        val pool = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.url
                username = config.user
                password = config.password
                maximumPoolSize = 10
                poolName = "dorai"
            },
        )
        return open(pool)
    }

    fun open(dataSource: DataSource): Database {
        Flyway.configure().dataSource(dataSource).load().migrate()
        return Database.connect(dataSource)
    }
}

/** Runs [block] in a transaction on the IO dispatcher, since JDBC blocks. */
suspend fun <T> Database.tx(block: JdbcTransaction.() -> T): T =
    withContext(Dispatchers.IO) { transaction(this@tx) { block() } }
