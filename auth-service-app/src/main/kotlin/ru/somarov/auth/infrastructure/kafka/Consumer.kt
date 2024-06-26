package ru.somarov.auth.infrastructure.kafka

import io.micrometer.core.instrument.kotlin.asContextElement
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import kotlinx.coroutines.reactor.mono
import kotlinx.datetime.Clock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.serializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kafka.receiver.KafkaReceiver
import reactor.kafka.receiver.ReceiverOptions
import reactor.kafka.receiver.observation.KafkaRecordReceiverContext
import reactor.util.retry.Retry
import ru.somarov.auth.presentation.event.Metadata
import java.time.Duration
import java.util.UUID
import java.util.function.Supplier

abstract class Consumer<T : Any>(
    private val registry: ObservationRegistry,
    private val clazz: Class<T>,
    private val props: ConsumerProps
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    private val id = UUID.randomUUID()

    private lateinit var disposable: Disposable

    fun start() {
        log.info("Starting ${props.name} consumer")

        val flux = buildReceiver().receiveAutoAck().delayElements(Duration.ofSeconds(props.delaySeconds))
            .concatMap { batch -> handleBatch(batch) }
            .doOnSubscribe { log.info("Consumer ${props.name} started") }
            .doOnTerminate { log.info("Consumer ${props.name} terminated") }
            .doOnError { throwable -> log.error("Got exception while processing records", throwable) }
            .retryWhen(
                Retry
                    .backoff(props.reconnectAttempts, Duration.ofSeconds(props.reconnectPeriodSeconds))
                    .jitter(props.reconnectJitter)
            )

        disposable = flux.subscribe()
    }

    fun stop() {
        disposable.dispose()
        log.info("Stopped ${props.name} consumer")
    }

    fun <R : Any> supports(clazz: Class<R>) = clazz == this.clazz

    fun getName() = props.name

    abstract suspend fun handleMessage(message: T, metadata: Metadata): Result
    abstract suspend fun onFailedMessage(e: Exception?, message: T, metadata: Metadata)

    private fun handleBatch(records: Flux<ConsumerRecord<String, T?>>): Mono<Long> {
        return records
            .groupBy { record -> record.partition() }
            .flatMap { partitionRecords ->
                if (props.strategy == ExecutionStrategy.SEQUENTIAL) {
                    // Process records within each partition sequentially
                    partitionRecords.concatMap { record -> handleRecord(record) }
                } else {
                    // Process records within each partition parallel
                    partitionRecords.flatMap { record -> handleRecord(record) }
                }.count()
            }
            .reduce(Long::plus) // Sum the counts across all partitions
            .map { totalProcessedRecords ->
                log.info("Completed batch of size $totalProcessedRecords")
                totalProcessedRecords
            }
    }

    private fun handleRecord(record: ConsumerRecord<String, T?>): Mono<Result> {
        val observation = Observation.createNotStarted(
            "kafka_observation",
            { KafkaRecordReceiverContext(record, props.name, id.toString()) },
            registry
        )

        val result: Mono<Result>? = observation.observe(Supplier {
            if (record.value() == null) {
                log.warn("Got empty value for record $record")
                // Would be great to send messages which cannot be serialized to dlq here
                Mono.just(Result(Result.Code.FAILED))
            } else {
                mono(registry.asContextElement()) {
                    handle(
                        record.value()!!,
                        Metadata(Clock.System.now(), record.key(), 0)
                    )
                }
            }
        })

        return result!!
    }

    @Suppress("TooGenericExceptionCaught") // Should be able to process every exception
    private suspend fun handle(message: T, metadata: Metadata): Result {
        log.info("Got $message with metadata $metadata to handle with retry")

        val result = try {
            handleMessage(message, metadata)
        } catch (e: Exception) {
            log.error("Got exception while processing event $message with metadata $metadata", e)
            onFailedMessage(e, message, metadata)
            Result(Result.Code.FAILED)
        }
        if (result.code == Result.Code.FAILED) {
            onFailedMessage(null, message, metadata)
        }
        return result
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Suppress("TooGenericExceptionCaught", "UNCHECKED_CAST")
    private fun buildReceiver(): KafkaReceiver<String, T?> {
        val consumerProps = HashMap<String, Any>()

        consumerProps[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = props.brokers
        consumerProps[ConsumerConfig.GROUP_ID_CONFIG] = props.groupId
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        consumerProps[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = props.maxPollRecords
        consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = props.offsetResetConfig

        val consProps = ReceiverOptions.create<String, T>(consumerProps)
            .commitInterval(Duration.ofSeconds(props.commitInterval))
            .withValueDeserializer { _, data ->
                try {
                    Cbor.Default.decodeFromByteArray(
                        Cbor.serializersModule.serializer(this.clazz) as KSerializer<T>,
                        data
                    )
                } catch (e: Exception) {
                    log.error("Got exception $e while trying to parse event from data $data")
                    null
                }
            }
            .subscription(listOf(props.topic))

        return KafkaReceiver.create(consProps)
    }

    data class ConsumerProps(
        val topic: String,
        val name: String = "Consumer_${topic}_${UUID.randomUUID()}",
        val delaySeconds: Long = 0,
        val strategy: ExecutionStrategy = ExecutionStrategy.PARALLEL,
        val enabled: Boolean = false,
        val brokers: String,
        val groupId: String,
        val offsetResetConfig: String,
        val commitInterval: Long,
        val maxPollRecords: Int,
        val reconnectAttempts: Long,
        val reconnectJitter: Double,
        val reconnectPeriodSeconds: Long
    )

    enum class ExecutionStrategy { PARALLEL, SEQUENTIAL }
}
