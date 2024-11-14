package ru.somarov.auth.lib.db

import io.ktor.server.application.*
import kotlin.time.Duration

data class DbProps(
    val host: String,
    val port: Int,
    val name: String,
    val schema: String,
    val user: String,
    val password: String,
    val connectionTimeout: Duration,
    val statementTimeout: Duration,
    val pool: DbPoolProps
) {

    data class DbPoolProps(
        val maxSize: Int,
        val minIdle: Int,
        val maxIdleTime: Duration,
        val maxLifeTime: Duration,
        val validationQuery: String,
    )

    companion object {
        fun parse(environment: ApplicationEnvironment): DbProps {
            return DbProps(
                host = environment.config.property("ktor.db.host").getString(),
                port = environment.config.property("ktor.db.port").getString().toInt(),
                name = environment.config.property("ktor.db.name").getString(),
                schema = environment.config.property("ktor.db.schema").getString(),
                user = environment.config.property("ktor.db.user").getString(),
                password = environment.config.property("ktor.db.password").getString(),
                connectionTimeout = Duration.parse(
                    environment.config.property("ktor.db.connection-timeout").getString()
                ),
                statementTimeout = Duration.parse(
                    environment.config.property("ktor.db.statement-timeout").getString()
                ),
                pool = DbPoolProps(
                    maxSize = environment.config.property("ktor.db.pool.max-size").getString().toInt(),
                    minIdle = environment.config.property("ktor.db.pool.min-idle").getString().toInt(),
                    maxIdleTime = Duration.parse(
                        environment.config.property("ktor.db.pool.max-idle-time").getString()
                    ),
                    maxLifeTime = Duration.parse(
                        environment.config.property("ktor.db.pool.max-life-time").getString()
                    ),
                    validationQuery = environment.config.property("ktor.db.pool.validation-query").getString()
                )
            )
        }
    }
}
