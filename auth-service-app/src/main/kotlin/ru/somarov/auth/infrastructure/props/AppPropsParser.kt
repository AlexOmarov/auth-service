package ru.somarov.auth.infrastructure.props

import io.ktor.server.application.ApplicationEnvironment
import kotlin.time.Duration

fun parseProps(environment: ApplicationEnvironment): AppProps {
    return AppProps(
        name = environment.config.property("application.name").getString(),
        instance = environment.config.property("application.instance").getString(),
        db = parseDbProps(environment),
        kafka = AppProps.KafkaProps(
            brokers = environment.config.property("application.kafka.brokers").getString(),
            messageRetryAttempts = environment.config.property("application.kafka.message-retry-attempts").getString()
                .toInt(),
            group = environment.config.property("application.kafka.group").getString(),
            reconnect = AppProps.KafkaConsumerReconnectProps(
                attempts = environment.config.property("application.kafka.reconnect.attempts").getString().toLong(),
                jitter = environment.config.property("application.kafka.reconnect.jitter").getString().toDouble(),
                periodSeconds = environment.config.property("application.kafka.reconnect.period-seconds").getString()
                    .toLong()
            ),
            consumers = parseConsumersProps(environment),
            producers = AppProps.KafkaProducersProps(
                dlq = AppProps.KafkaProducerProps(
                    enabled = environment.config.property("application.kafka.producers.retry.enabled")
                        .getString().toBoolean(),
                    topic = environment.config.property("application.kafka.producers.retry.topic")
                        .getString(),
                    maxInFlight = environment.config.property("application.kafka.producers.retry.max-in-flight")
                        .getString().toInt()
                ), retry = AppProps.KafkaProducerProps(
                    enabled = environment.config.property("application.kafka.producers.dlq.enabled")
                        .getString().toBoolean(),
                    topic = environment.config.property("application.kafka.producers.dlq.topic")
                        .getString(),
                    maxInFlight = environment.config.property("application.kafka.producers.dlq.max-in-flight")
                        .getString().toInt()

                )
            )
        ),
        otel = AppProps.OtelProps(
            protocol = environment.config.property("application.otel.protocol").getString(),
            host = environment.config.property("application.otel.host").getString(),
            logsPort = environment.config.property("application.otel.logs-port").getString().toShort(),
            metricsPort = environment.config.property("application.otel.metrics-port").getString().toShort(),
            tracingPort = environment.config.property("application.otel.tracing-port").getString().toShort(),
            tracingProbability = environment.config.property("application.otel.tracing-probability").getString()
                .toDouble()
        )
    )
}

fun parseConsumersProps(environment: ApplicationEnvironment): AppProps.KafkaConsumersProps {
    return AppProps.KafkaConsumersProps(
        mail = AppProps.KafkaConsumerProps(
            enabled = environment.config.property("application.kafka.consumers.mail.enabled").getString()
                .toBoolean(),
            topic = environment.config.property("application.kafka.consumers.mail.topic").getString(),
            name = environment.config.property("application.kafka.consumers.mail.name").getString(),
            delay = Duration.parse(
                environment.config.property("application.kafka.consumers.mail.delay").getString()
            ),
            reset = AppProps.KafkaResetConfig.valueOf(
                environment.config.property("application.kafka.consumers.mail.reset").getString().uppercase()
            ),
            commitInterval = Duration.parse(
                environment.config.property("application.kafka.consumers.mail.commit-interval").getString()
            ),
            maxPollRecords = environment.config.property("application.kafka.consumers.mail.max-poll-records")
                .getString().toInt()
        ), retry = AppProps.KafkaConsumerProps(
            enabled = environment.config.property("application.kafka.consumers.retry.enabled").getString()
                .toBoolean(),
            topic = environment.config.property("application.kafka.consumers.retry.topic").getString(),
            name = environment.config.property("application.kafka.consumers.retry.name").getString(),
            delay = Duration.parse(
                environment.config.property("application.kafka.consumers.retry.delay").getString()
            ),
            reset = AppProps.KafkaResetConfig.valueOf(
                environment.config.property("application.kafka.consumers.retry.reset").getString().uppercase()
            ),
            commitInterval = Duration.parse(
                environment.config.property("application.kafka.consumers.retry.commit-interval").getString()
            ),
            maxPollRecords = environment.config.property("application.kafka.consumers.retry.max-poll-records")
                .getString().toInt()
        )
    )
}

private fun parseDbProps(environment: ApplicationEnvironment): AppProps.DbProps {
    return AppProps.DbProps(
        host = environment.config.property("application.db.host").getString(),
        port = environment.config.property("application.db.port").getString().toInt(),
        name = environment.config.property("application.db.name").getString(),
        schema = environment.config.property("application.db.schema").getString(),
        user = environment.config.property("application.db.user").getString(),
        password = environment.config.property("application.db.password").getString(),
        connectionTimeout = Duration.parse(
            environment.config.property("application.db.connection-timeout").getString()
        ),
        statementTimeout = Duration.parse(
            environment.config.property("application.db.statement-timeout").getString()
        ),
        pool = AppProps.DbPoolProps(
            maxSize = environment.config.property("application.db.pool.max-size").getString().toInt(),
            minIdle = environment.config.property("application.db.pool.min-idle").getString().toInt(),
            maxIdleTime = Duration.parse(
                environment.config.property("application.db.pool.max-idle-time").getString()
            ),
            maxLifeTime = Duration.parse(
                environment.config.property("application.db.pool.max-life-time").getString()
            ),
            validationQuery = environment.config.property("application.db.pool.validation-query").getString()
        )
    )
}
