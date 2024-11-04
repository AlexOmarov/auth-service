package ru.somarov.auth.config

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.kafka.KafkaContainer

object BaseIntegrationTest {

    private val postgresql = PostgreSQLContainer<Nothing>("postgres:16.3")
        .apply {
            withReuse(true)
            start()
        }
    private val kafka = KafkaContainer("apache/kafka")
        .apply {
            withReuse(true)
            start()
        }

    private val env = MapApplicationConfig(
        "ktor.db.port" to postgresql.firstMappedPort.toString(),
        "ktor.db.host" to postgresql.host,
        "ktor.db.user" to postgresql.username,
        "ktor.db.password" to postgresql.password,
        "ktor.db.name" to postgresql.databaseName,
        "ktor.kafka.brokers" to kafka.bootstrapServers,
    )

    fun execute(func: suspend (ApplicationTestBuilder) -> Unit) {
        testApplication {
            environment { config = config.mergeWith(ApplicationConfig("application.yaml")).mergeWith(env) }
            func(this)
        }
    }
}
