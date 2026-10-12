
package com.vetplatform

import org.flywaydb.core.Flyway

object FlywayFactory {

    fun migrate() {
        val url = requireNotNull(System.getenv("DB_URL")) {
            "DB_URL environment variable is missing"
        }

        val user = requireNotNull(System.getenv("DB_USER")) {
            "DB_USER environment variable is missing"
        }

        val password = requireNotNull(System.getenv("DB_PASSWORD")) {
            "DB_PASSWORD environment variable is missing"
        }

        Flyway.configure()
            .dataSource(url, user, password)
            .locations("classpath:db/migration")
            .validateMigrationNaming(true)
            .load()
            .migrate()
    }
}
