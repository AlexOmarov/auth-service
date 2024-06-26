package ru.somarov.auth.presentation.consumers

import io.micrometer.observation.ObservationRegistry
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import ru.somarov.auth.infrastructure.kafka.Consumer
import ru.somarov.auth.infrastructure.kafka.Producer
import ru.somarov.auth.infrastructure.kafka.Result
import ru.somarov.auth.infrastructure.props.AppProps
import ru.somarov.auth.presentation.event.Metadata
import ru.somarov.auth.presentation.event.RetryMessage
import java.util.UUID
import kotlin.time.DurationUnit

@Suppress("UNCHECKED_CAST")
class RetryConsumer(
    private val props: AppProps.KafkaProps,
    private val consumers: List<Consumer<out Any>>,
    registry: ObservationRegistry,
) : Consumer<RetryMessage<Any>>(
    props = ConsumerProps(
        topic = props.consumers.retry.topic,
        name = "Consumer_${props.consumers.retry.topic}_${props.consumers.retry.name}_${UUID.randomUUID()}",
        delaySeconds = props.consumers.retry.delay.toLong(DurationUnit.MILLISECONDS),
        strategy = ExecutionStrategy.PARALLEL,
        enabled = props.consumers.retry.enabled,
        brokers = props.brokers,
        groupId = props.group,
        offsetResetConfig = props.consumers.retry.reset.name.lowercase(),
        commitInterval = props.consumers.retry.commitInterval.toLong(DurationUnit.MILLISECONDS),
        maxPollRecords = props.consumers.retry.maxPollRecords,
        reconnectAttempts = props.reconnect.attempts,
        reconnectJitter = props.reconnect.jitter,
        reconnectPeriodSeconds = props.reconnect.periodSeconds
    ),
    registry = registry,
    clazz = RetryMessage::class.java as Class<RetryMessage<Any>>
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    @Suppress("UNCHECKED_CAST")
    private val retryProducer = Producer(
        Producer.ProducerProps(
            props.brokers,
            props.producers.retry.maxInFlight,
            props.producers.retry.topic,
        ), registry, RetryMessage::class.java as Class<RetryMessage<out Any>>
    )

    @Suppress("UNCHECKED_CAST")
    private val dlqProducer = Producer(
        Producer.ProducerProps(
            props.brokers,
            props.producers.dlq.maxInFlight,
            props.producers.dlq.topic,
        ), registry, RetryMessage::class.java as Class<RetryMessage<out Any>>
    )

    override suspend fun handleMessage(
        message: RetryMessage<Any>,
        metadata: Metadata
    ): Result {
        return if (props.messageRetryAttempts > message.attempt) retryMessage(message) else Result(Result.Code.FAILED)
    }

    override suspend fun onFailedMessage(e: Exception?, message: RetryMessage<Any>, metadata: Metadata) {
        log.error("Got unsuccessful message processing: $message, exception ${e?.message}", e)
        if (metadata.attempt < props.messageRetryAttempts && props.producers.retry.enabled)
            retryProducer.send(message, Metadata(metadata.createdAt, metadata.key, metadata.attempt + 1))
        else if (props.producers.dlq.enabled)
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
