package ru.somarov.auth.infrastructure.kafka

import io.micrometer.observation.ObservationRegistry
import io.micrometer.tracing.TraceContext
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.TraceId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.reactive.asFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.kafka.sender.KafkaSender
import reactor.kafka.sender.SenderOptions
import reactor.kafka.sender.SenderRecord
import reactor.kafka.sender.SenderResult
import ru.somarov.auth.presentation.event.Metadata
import java.util.UUID
import kotlin.random.Random

class Producer<T : Any>(
    private val props: ProducerProps,
    private val registry: ObservationRegistry,
    private val clazz: Class<T>,
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    private val sender = KafkaSender.create(createSenderOptions<T>())

    suspend fun send(event: T, metadata: Metadata): SenderResult<UUID> {
        val partition = null
        val timestamp = null
        val traceParent = createTraceParentHeader()
        val headers = mutableListOf<Header>(
            RecordHeader(TRACE_HEADER_KEY, traceParent.toByteArray()),
            RecordHeader(PAYLOAD_TYPE_HEADER_NAME, (event::class.qualifiedName ?: "").toByteArray())
        )

        val record = ProducerRecord(
            /* topic = */ props.topic,
            /* partition = */ partition,
            /* timestamp = */ timestamp,
            /* key = */ metadata.key,
            /* value = */ event,
            /* headers = */ headers
        )

        val result = sender.send(Flux.just(SenderRecord.create(record, UUID.randomUUID())))
            .doOnError { e -> log.error("Send failed", e) }
            .asFlow()
            .first()

        log.info(
            "Message has been sent: ${result.correlationMetadata()} " +
                "traceParent: $traceParent, " +
                "topic/partition: ${result.recordMetadata().topic()}/${result.recordMetadata().partition()}, " +
                "offset: ${result.recordMetadata().offset()}, " +
                "timestamp: ${result.recordMetadata().timestamp()}"
        )

        return result
    }

    private fun createTraceParentHeader(): String {
        val min = Random.nextLong(2L, Long.MAX_VALUE - 2)
        val max = Random.nextLong(min, Long.MAX_VALUE)
        var traceId = TraceId.fromLongs(max, min)
        var spanId = SpanId.fromLong(Random.nextLong())

        val traceContext = registry.currentObservation?.context?.get<TraceContext>(TraceContext::class.java)

        if (traceContext != null) {
            traceId = traceContext.traceId()
            spanId = traceContext.spanId()
        }

        return "00-$traceId-$spanId-01"
    }

    @Suppress("TooGenericExceptionCaught", "UNCHECKED_CAST")
    private fun <T> createSenderOptions(): SenderOptions<String, T> {
        val producerProps: MutableMap<String, Any> = HashMap()

        producerProps[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = props.brokers
        producerProps[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        producerProps[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java

        return SenderOptions
            .create<String, T>(producerProps)
            .withValueSerializer { _, data ->
                try {
                    Json.Default.encodeToString(
                        Json.serializersModule.serializer(this.clazz) as KSerializer<T>,
                        data
                    ).toByteArray()
                } catch (e: Exception) {
                    throw SerializationException("Error when serializing event to byte[]", e)
                }
            }
            .maxInFlight(props.maxInFlight)
    }

    companion object {
        private const val TRACE_HEADER_KEY = "traceparent"
        private const val PAYLOAD_TYPE_HEADER_NAME = "PAYLOAD_TYPE"
    }

    data class ProducerProps(val brokers: String, val maxInFlight: Int, val topic: String)
}
