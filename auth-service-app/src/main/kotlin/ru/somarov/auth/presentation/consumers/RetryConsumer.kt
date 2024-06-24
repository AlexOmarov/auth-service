package ru.somarov.auth.presentation.consumers

import io.ktor.server.application.ApplicationEnvironment
import io.micrometer.observation.ObservationRegistry
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import ru.somarov.auth.infrastructure.kafka.Consumer
import ru.somarov.auth.infrastructure.kafka.Producer
import ru.somarov.auth.infrastructure.kafka.Result
import ru.somarov.auth.presentation.event.Metadata
import ru.somarov.auth.presentation.event.RetryMessage

@Suppress("UNCHECKED_CAST")
class RetryConsumer(
    env: ApplicationEnvironment,
    private val consumers: List<Consumer<out Any>>,
    registry: ObservationRegistry,
) : Consumer<RetryMessage<Any>>(
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
    clazz = RetryMessage::class.java as Class<RetryMessage<Any>>
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    val brokers = env.config.property("application.kafka.brokers").getString()

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

    override suspend fun handleMessage(
        message: RetryMessage<Any>,
        metadata: Metadata
    ): Result {
        return if (retryResendNumber > message.attempt) retryMessage(message) else Result(Result.Code.FAILED)
    }

    override suspend fun onFailedMessage(e: Exception?, message: RetryMessage<Any>, metadata: Metadata) {
        log.error("Got unsuccessful message processing: $message, exception ${e?.message}", e)
        if (metadata.attempt < retryResendNumber)
            retryProducer.send(message, Metadata(metadata.createdAt, metadata.key, metadata.attempt + 1))
        else
            dlqProducer.send(message, Metadata(metadata.createdAt, metadata.key, metadata.attempt + 1))
    }

    private suspend fun retryMessage(message: RetryMessage<Any>): Result {
        return if (message.payload::class != RetryMessage::class) {
            val consumer = consumers.firstOrNull { it.supports(message.payload::class.java) } as Consumer<Any>?
            if (consumer == null) {
                log.warn("Cannot find consumer for message $message. Skip message processing")
                Result(Result.Code.OK)
            } else {
                consumer.handleMessage(
                    message.payload,
                    Metadata(
                        Clock.System.now(),
                        message.key,
                        message.attempt
                    )
                )
            }
        } else {
            throw IllegalArgumentException("Got retry message $message with payload not of type CommonEvent")
        }
    }
}
