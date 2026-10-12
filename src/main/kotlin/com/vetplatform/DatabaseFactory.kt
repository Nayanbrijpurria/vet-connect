
package com.vetplatform

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DatabaseFactory {

    private val dataSource: HikariDataSource by lazy {
        val config = HikariConfig().apply {
            jdbcUrl = requireNotNull(System.getenv("DB_URL")) {
                "DB_URL environment variable is missing"
            }
            username = requireNotNull(System.getenv("DB_USER")) {
                "DB_USER environment variable is missing"
            }
            password = requireNotNull(System.getenv("DB_PASSWORD")) {
                "DB_PASSWORD environment variable is missing"
            }

            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 5
            minimumIdle = 1
            connectionTimeout = 5000
            poolName = "VetPlatformPool"
        }

        HikariDataSource(config)
    }

    suspend fun checkConnection(): Boolean =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT 1").use { result ->
                        result.next() && result.getInt(1) == 1
                    }
                }
            }
        }

    fun close() {
        dataSource.close()
    }

    suspend fun <T> withConnection(
        block: (java.sql.Connection) -> T
    ): T = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            block(connection)
        }
    }
}
