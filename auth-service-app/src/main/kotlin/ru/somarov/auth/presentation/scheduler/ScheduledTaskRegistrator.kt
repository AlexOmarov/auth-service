package ru.somarov.auth.presentation.scheduler

import io.ktor.util.copy
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readBytes
import io.micrometer.observation.ObservationRegistry
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufUtil.isText
import io.netty.buffer.CompositeByteBuf
import io.netty.buffer.Unpooled
import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.internal.BufferPool
import io.rsocket.kotlin.metadata.CompositeMetadata.Reader.read
import io.rsocket.kotlin.metadata.RoutingMetadata
import io.rsocket.kotlin.metadata.buildCompositeMetadata
import io.rsocket.kotlin.metadata.metadata
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import io.rsocket.metadata.CompositeMetadata
import io.rsocket.metadata.CompositeMetadataCodec
import io.rsocket.metadata.WellKnownMimeType
import io.rsocket.micrometer.observation.ObservationRequesterRSocketProxy
import io.rsocket.util.DefaultPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.javacrumbs.shedlock.core.LockConfiguration
import reactor.core.publisher.Mono
import ru.somarov.auth.infrastructure.micrometer.observeSuspendedMono
import ru.somarov.auth.infrastructure.scheduler.Scheduler
import ru.somarov.auth.presentation.request.AuthorizationRequest
import java.nio.charset.Charset
import java.time.Duration
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalMetadataApi::class)
fun registerTasks(scheduler: Scheduler, client: RSocket, observationRegistry: ObservationRegistry) {
    val config = LockConfiguration(
        Instant.now(),
        "TEST_TASK",
        Duration.parse("PT1M"),
        Duration.parse("PT1S"),
    )

    scheduler.register(config) {
        val request = buildPayload {
            data(Json.Default.encodeToString(AuthorizationRequest(UUID.randomUUID())))
            metadata(buildCompositeMetadata {
                add(RoutingMetadata("login"))
            })
        }

        val metadata = CompositeByteBuf(ByteBufAllocator.DEFAULT, true, 4000)
        request.metadata?.copy()?.read(BufferPool.Default)?.entries?.forEach {
            CompositeMetadataCodec.encodeAndAddMetadata(
                metadata,
                ByteBufAllocator.DEFAULT,
                it.mimeType.toString(),
                Unpooled.wrappedBuffer(it.content.readBytes())
            )
        }
        val res = ObservationRequesterRSocketProxy(
            getRSocketWrapper(client, observationRegistry),
            observationRegistry
        ).requestResponse(
            DefaultPayload.create(
                Unpooled.wrappedBuffer(request.data.readBytes()),
                metadata
            )
        ).contextCapture().awaitSingle()
        val response = Payload(
            ByteReadChannel(res.data).readRemaining(),
            ByteReadChannel(res.metadata).readRemaining()
        )
        val result = String(response.data.readBytes())
        KtorSimpleLogger("TEST").info(result)
    }
}

private fun getRSocketWrapper(input: RSocket, observationRegistry: ObservationRegistry): io.rsocket.RSocket {
    val logger = KtorSimpleLogger("WOW")
    return object : io.rsocket.RSocket {
        override fun requestResponse(payload: io.rsocket.Payload): Mono<io.rsocket.Payload> {
            val context = (Dispatchers.IO + input.coroutineContext).minusKey(Job().key)
            val observation = observationRegistry.currentObservation!!
            return observation.observeSuspendedMono(coroutineContext = context) {
                val deserializedRequest = getDeserializedPayload(payload)
                logger.info(
                    "Outgoing rsocket request <- ${deserializedRequest.third}: " +
                        "payload: ${deserializedRequest.first}, metadata: ${deserializedRequest.second}"
                )

                val result = input.requestResponse(
                    Payload(
                        ByteReadChannel(payload.data).readRemaining(),
                        ByteReadChannel(payload.metadata).readRemaining()
                    )
                )
                val response = DefaultPayload.create(result.data.readBytes(), result.metadata?.readBytes())

                val deserializedResponse = getDeserializedPayload(response)
                logger.info(
                    "Incoming rsocket response -> ${deserializedRequest.third}: " +
                        "payload: ${deserializedResponse.first}, " +
                        "request metadata: ${deserializedRequest.second}, " +
                        "response metadata: ${deserializedResponse.second}"
                )
                return@observeSuspendedMono response
            }.contextCapture()
        }
    }
}

private fun getDeserializedPayload(payload: io.rsocket.Payload): Triple<Any, List<String>, String> {
    val logger = KtorSimpleLogger("WPWPW")
    val encoding: Charset = Charset.forName("UTF8")
    val data = try {
        val array = payload.data.copy().array()
        if (array.isNotEmpty()) {
            String(array)
        } else {
            "Body is null"
        }
    } catch (e: SerializationException) {
        logger.error("Got error while deserializing json to string", e)
        "Body is null"
    }
    var routing = "null"
    val metadata = CompositeMetadata(Unpooled.wrappedBuffer(payload.metadata.copy().array()), false)
        .map { met ->
            val content = if (isText(met.content, encoding)) met.content.toString(encoding) else "Not text"
            if (met.mimeType == WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.string) {
                routing = if (content.isNotEmpty()) content.substring(1) else content
                "Header(mime: ${met.mimeType}, content: $routing)"
            } else {
                "Header(mime: ${met.mimeType}, content: $content)"
            }
        }

    return Triple(data, metadata, routing)
}
