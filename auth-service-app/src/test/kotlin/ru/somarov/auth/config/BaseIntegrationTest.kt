package ru.somarov.auth.config

import io.ktor.server.config.MapApplicationConfig
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import reactor.core.publisher.Hooks

object BaseIntegrationTest {

    private var postgresql = PostgreSQLContainer<Nothing>("postgres:16.3").apply {
        withReuse(true)
        start()
    }
    private var kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.2")).apply {
        withReuse(true)
        start()
    }

    val env = MapApplicationConfig(
        "ktor.db.port" to postgresql.firstMappedPort.toString(),
        "ktor.db.host" to postgresql.host,
        "ktor.db.user" to postgresql.username,
        "ktor.db.password" to postgresql.password,
        "ktor.db.name" to postgresql.databaseName,
        "ktor.kafka.brokers" to kafka.bootstrapServers,
    )

    init {
        // Still doesn't add header to request with this, fix is needed
        Hooks.enableAutomaticContextPropagation()
    }
}
