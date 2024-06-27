package ru.somarov.auth.presentation.consumers

import io.micrometer.observation.ObservationRegistry
import org.slf4j.LoggerFactory
import ru.somarov.auth.application.service.Service
import ru.somarov.auth.infrastructure.kafka.Consumer
import ru.somarov.auth.infrastructure.kafka.Producer
import ru.somarov.auth.infrastructure.kafka.Result
import ru.somarov.auth.infrastructure.props.AppProps
import ru.somarov.auth.presentation.event.Metadata
import ru.somarov.auth.presentation.event.RetryMessage
import ru.somarov.auth.presentation.event.broadcast.MailBroadcast
import java.util.UUID

class MailConsumer(
    private val service: Service,
    private val props: AppProps.KafkaProps,
    registry: ObservationRegistry,
) : Consumer<MailBroadcast>(
    props = ConsumerProps(
        topic = props.consumers.mail.topic,
        name = "Consumer_${props.consumers.mail.topic}_${props.consumers.mail.name}_${UUID.randomUUID()}",
        delayMs = props.consumers.mail.delay,
        strategy = ExecutionStrategy.PARALLEL,
        enabled = props.consumers.mail.enabled,
        brokers = props.brokers,
        groupId = props.group,
        offsetResetConfig = props.consumers.mail.reset.name.lowercase(),
        commitInterval = props.consumers.mail.commitInterval,
        maxPollRecords = props.consumers.mail.maxPollRecords,
        reconnectAttempts = props.reconnect.attempts,
        reconnectJitter = props.reconnect.jitter,
        reconnectPeriodSeconds = props.reconnect.periodSeconds
    ),
    registry = registry,
    clazz = MailBroadcast::class.java
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

    override suspend fun handleMessage(message: MailBroadcast, metadata: Metadata): Result {
        service.makeWork(message.toString())
        return Result(Result.Code.OK)
    }

    override suspend fun onFailedMessage(e: Exception?, message: MailBroadcast, metadata: Metadata) {
        log.error("Got unsuccessful message processing: $message, exception ${e?.message}", e)
        val retryMessage = RetryMessage(payload = message, key = metadata.key, attempt = metadata.attempt + 1)
        if (metadata.attempt < props.messageRetryAttempts && props.producers.retry.enabled)
            retryProducer.send(retryMessage, metadata)
        else if (props.producers.dlq.enabled)
            dlqProducer.send(retryMessage, metadata)
    }
}
