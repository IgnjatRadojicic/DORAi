package dorai.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

/** Where the PostgreSQL database is. The password only ever comes from the environment. */
data class DatabaseConfig(val url: String, val user: String, val password: String) {
    override fun toString() = "DatabaseConfig(url=$url, user=$user)"

    companion object {
        /** Reads `DORAI_DB_URL`, `DORAI_DB_USER` and `DORAI_DB_PASSWORD`; null when no URL is set. */
        fun fromEnv(env: Map<String, String> = System.getenv()): DatabaseConfig? {
            val url = env["DORAI_DB_URL"]?.takeIf { it.isNotBlank() } ?: return null
            return DatabaseConfig(url, env["DORAI_DB_USER"] ?: "dorai", env["DORAI_DB_PASSWORD"] ?: "")
        }
    }
}

/** Opens a connection pool, applies the Flyway migrations and connects Exposed to it. */
fun connectDatabase(config: DatabaseConfig): Database {
    val dataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
            maximumPoolSize = 10
        },
    )
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate()
    return Database.connect(dataSource)
}
