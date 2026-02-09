package com.printnest.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Database")

/**
 * Configure database connection using HikariCP connection pool
 */
fun Application.configureDatabase() {
    val dbUrl = System.getenv("DATABASE_URL")
        ?: environment.config.propertyOrNull("database.url")?.getString()
        ?: "jdbc:postgresql://localhost:5433/printnest"

    val dbUser = System.getenv("DATABASE_USER")
        ?: environment.config.propertyOrNull("database.user")?.getString()
        ?: "postgres"

    val dbPassword = System.getenv("DATABASE_PASSWORD")
        ?: environment.config.propertyOrNull("database.password")?.getString()
        ?: "postgres"

    val dbDriver = environment.config.propertyOrNull("database.driver")?.getString()
        ?: "org.postgresql.Driver"

    logger.info("Connecting to database: $dbUrl")

    val hikariConfig = HikariConfig().apply {
        driverClassName = dbDriver
        jdbcUrl = dbUrl
        username = dbUser
        password = dbPassword

        // Pool settings
        maximumPoolSize = System.getenv("DATABASE_POOL_MAX_SIZE")?.toIntOrNull() ?: 20
        minimumIdle = System.getenv("DATABASE_POOL_MIN_IDLE")?.toIntOrNull() ?: 5
        idleTimeout = 600000
        connectionTimeout = 30000
        maxLifetime = 1800000
        leakDetectionThreshold = 60000

        // PostgreSQL specific
        addDataSourceProperty("cachePrepStmts", "true")
        addDataSourceProperty("prepStmtCacheSize", "250")
        addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
    }

    val dataSource = HikariDataSource(hikariConfig)
    Database.connect(dataSource)

    logger.info("Database connection established successfully")
}
