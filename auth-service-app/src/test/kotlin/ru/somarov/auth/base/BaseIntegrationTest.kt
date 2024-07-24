package ru.somarov.auth.base

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import reactor.core.publisher.Hooks
import java.lang.management.ManagementFactory
import javax.management.ObjectName

@Testcontainers
abstract class BaseIntegrationTest {

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

    fun execute(func: suspend (ApplicationTestBuilder) -> Unit) {
        testApplication {
            environment { config = config.mergeWith(ApplicationConfig("application.yaml")).mergeWith(env) }
            func(this)
        }
        ManagementFactory.getPlatformMBeanServer().unregisterMBean(
            ObjectName("io.r2dbc.pool:name=auth-service_pool,type=ConnectionPool")
        )
    }

    companion object {
        private var postgresql = PostgreSQLContainer<Nothing>("postgres:16.3").apply {
            withReuse(true)
            start()
        }
        private var kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.2")).apply {
            withReuse(true)
            start()
        }

        init {
            System.setProperty("KAFKA_BROKERS", kafka.bootstrapServers)

            System.setProperty("DB_HOST", postgresql.host)
            System.setProperty("DB_PORT", postgresql.firstMappedPort.toString())
            System.setProperty("DB_NAME", postgresql.databaseName)
            System.setProperty("DB_USER", postgresql.username)
            System.setProperty("DB_PASSWORD", postgresql.password)
        }
    }
}
