package ru.somarov.auth.infrastructure.props

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
        val messageRetryAttempts: Int,
        val group: String,
        val reconnect: KafkaConsumerReconnectProps,
        val consumers: KafkaConsumersProps,
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

    data class KafkaConsumersProps(
        val mail: KafkaConsumerProps,
        val retry: KafkaConsumerProps
    )

    data class KafkaConsumerProps(
        val enabled: Boolean,
        val topic: String,
        val name: String,
        val delay: Long,
        val reset: KafkaResetConfig,
        val commitInterval: Long,
        val maxPollRecords: Int
    )

    data class KafkaConsumerReconnectProps(
        val attempts: Long,
        val jitter: Double,
        val periodSeconds: Long,
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

    enum class KafkaResetConfig {
        EARLIEST, LATEST
    }
}
