package ru.somarov.auth.presentation.consumers

import io.ktor.server.application.ApplicationEnvironment
import io.micrometer.observation.ObservationRegistry
import org.slf4j.LoggerFactory
import ru.somarov.auth.application.Service
import ru.somarov.auth.infrastructure.kafka.Consumer
import ru.somarov.auth.infrastructure.kafka.Producer
import ru.somarov.auth.infrastructure.kafka.Result
import ru.somarov.auth.presentation.event.Metadata
import ru.somarov.auth.presentation.event.RetryMessage

class MailConsumer(
    private val service: Service,
    env: ApplicationEnvironment,
    registry: ObservationRegistry,
) : Consumer<String>(
    props = ConsumerProps(
        topic = env.config.property("application.kafka.topic").getString(),
        name = env.config.property("application.kafka.name").getString(),
        delaySeconds = env.config.property("application.kafka.delay").getString().toLong(),
        strategy = ExecutionStrategy.PARALLEL,
        enabled = env.config.property("application.kafka.enabled").getString().toBoolean(),
        brokers = env.config.property("application.kafka.brokers").getString(),
        groupId = env.config.property("application.kafka.group-id").getString(),
        offsetResetConfig = env.config.property("application.kafka.reset-config").getString(),
        commitInterval = env.config.property("application.kafka.commit-interval").getString().toLong(),
        maxPollRecords = env.config.property("application.kafka.max-poll-records").getString().toInt(),
        reconnectAttempts = env.config.property("application.kafka.reconnect-attempts").getString().toLong(),
        reconnectJitter = env.config.property("application.kafka.reconnect-jitter").getString().toDouble(),
        reconnectPeriodSeconds = env.config.property("application.kafka.reconnect-period-seconds").getString().toLong()
    ),
    registry = registry,
    clazz = String::class.java
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    private val brokers = env.config.property("application.kafka.brokers").getString()

    private val retryResendNumber = env.config.property("application.kafka.retry-resend-number").getString().toInt()

    @Suppress("UNCHECKED_CAST")
    private val retryProducer = Producer(
        Producer.ProducerProps(
            brokers,
            env.config.property("application.kafka.max-in-flight").getString().toInt(),
            env.config.property("application.kafka.retry-topic").getString()
        ), registry, RetryMessage::class.java as Class<RetryMessage<out Any>>
    )

    @Suppress("UNCHECKED_CAST")
    private val dlqProducer = Producer(
        Producer.ProducerProps(
            brokers,
            env.config.property("application.kafka.max-in-flight").getString().toInt(),
            env.config.property("application.kafka.dlq-topic").getString()
        ), registry, RetryMessage::class.java as Class<RetryMessage<out Any>>
    )

    override suspend fun handleMessage(message: String, metadata: Metadata): Result {
        service.makeWork()
        return Result(Result.Code.OK)
    }

    override suspend fun onFailedMessage(e: Exception?, message: String, metadata: Metadata) {
        log.error("Got unsuccessful message processing: $message, exception ${e?.message}", e)
        val retryMessage = RetryMessage(payload = message, key = metadata.key, attempt = metadata.attempt + 1)
        if (metadata.attempt < retryResendNumber)
            retryProducer.send(retryMessage, metadata)
        else
            dlqProducer.send(retryMessage, metadata)
    }
}
