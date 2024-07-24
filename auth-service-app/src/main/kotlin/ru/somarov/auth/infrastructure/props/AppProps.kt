package ru.somarov.auth.infrastructure.props

import io.ktor.server.application.ApplicationEnvironment
import kotlin.time.Duration

data class AppProps(
    val name: String,
    val instance: String,
    val db: DbProps,
    val kafka: KafkaProps,
    val otel: OtelProps,
) {
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
    )

    data class KafkaProps(
        val brokers: String,
        val producers: KafkaProducersProps,
    )

    data class KafkaProducersProps(
        val dlq: KafkaProducerProps,
        val retry: KafkaProducerProps
    )

    data class KafkaProducerProps(
        val enabled: Boolean,
        val topic: String,
        val maxInFlight: Int
    )

    data class DbPoolProps(
        val maxSize: Int,
        val minIdle: Int,
        val maxIdleTime: Duration,
        val maxLifeTime: Duration,
        val validationQuery: String,
    )

    data class OtelProps(
        val protocol: String,
        val host: String,
        val logsPort: Short,
        val metricsPort: Short,
        val tracingPort: Short,
        val tracingProbability: Double
    )

    companion object {
        fun parseProps(environment: ApplicationEnvironment): AppProps {
            return AppProps(
                name = environment.config.property("ktor.name").getString(),
                instance = environment.config.property("ktor.instance").getString(),
                db = parseDbProps(environment),
                kafka = KafkaProps(
                    brokers = environment.config.property("ktor.kafka.brokers").getString(),
                    producers = KafkaProducersProps(
                        dlq = KafkaProducerProps(
                            enabled = environment.config.property("ktor.kafka.producers.retry.enabled")
                                .getString().toBoolean(),
                            topic = environment.config.property("ktor.kafka.producers.retry.topic")
                                .getString(),
                            maxInFlight = environment.config
                                .property("ktor.kafka.producers.retry.max-in-flight").getString().toInt()
                        ), retry = KafkaProducerProps(
                            enabled = environment.config.property("ktor.kafka.producers.dlq.enabled")
                                .getString().toBoolean(),
                            topic = environment.config.property("ktor.kafka.producers.dlq.topic")
                                .getString(),
                            maxInFlight = environment.config.property("ktor.kafka.producers.dlq.max-in-flight")
                                .getString().toInt()

                        )
                    )
                ),
                otel = OtelProps(
                    protocol = environment.config.property("ktor.otel.protocol").getString(),
                    host = environment.config.property("ktor.otel.host").getString(),
                    logsPort = environment.config.property("ktor.otel.logs-port").getString().toShort(),
                    metricsPort = environment.config.property("ktor.otel.metrics-port").getString().toShort(),
                    tracingPort = environment.config.property("ktor.otel.tracing-port").getString().toShort(),
                    tracingProbability = environment.config.property("ktor.otel.tracing-probability").getString()
                        .toDouble()
                )
            )
        }

        private fun parseDbProps(environment: ApplicationEnvironment): DbProps {
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
