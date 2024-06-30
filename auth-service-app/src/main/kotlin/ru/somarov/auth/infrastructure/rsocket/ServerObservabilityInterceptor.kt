package ru.somarov.auth.infrastructure.rsocket

import io.ktor.util.copy
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readBytes
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationRegistry
import io.netty.buffer.ByteBufUtil.isText
import io.netty.buffer.Unpooled
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.core.Interceptor
import io.rsocket.kotlin.payload.Payload
import io.rsocket.metadata.CompositeMetadata
import io.rsocket.metadata.WellKnownMimeType
import io.rsocket.micrometer.MicrometerRSocketInterceptor
import io.rsocket.micrometer.observation.ObservationResponderRSocketProxy
import io.rsocket.util.DefaultPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import kotlinx.serialization.SerializationException
import reactor.core.publisher.Mono
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext

internal class ServerObservabilityInterceptor(
    private val meterRegistry: MeterRegistry,
    private val observationRegistry: ObservationRegistry
) : Interceptor<RSocket> {

    private val logger = KtorSimpleLogger(this.javaClass.name)
    private val encoding: Charset = Charset.forName("UTF8")

    override fun intercept(input: RSocket): RSocket {
        val wrapper = getRSocketWrapper(input)
        val measuredRSocket = MicrometerRSocketInterceptor(meterRegistry).apply(wrapper) as io.rsocket.RSocket
        val proxy = ObservationResponderRSocketProxy(measuredRSocket, observationRegistry)

        return object : RSocket {
            override val coroutineContext: CoroutineContext
                get() = input.coroutineContext

            override suspend fun requestResponse(payload: Payload): Payload {
                val result = proxy.requestResponse(
                    DefaultPayload.create(
                        payload.data.readBytes(),
                        payload.metadata?.readBytes()
                    )
                ).awaitSingle()

                val response = Payload(
                    ByteReadChannel(result.data).readRemaining(),
                    ByteReadChannel(result.metadata).readRemaining()
                )
                return response
            }
        }
    }

    private fun getRSocketWrapper(input: RSocket): io.rsocket.RSocket {
        return object : io.rsocket.RSocket {
            override fun requestResponse(payload: io.rsocket.Payload): Mono<io.rsocket.Payload> {
                val context = (Dispatchers.IO + input.coroutineContext).minusKey(Job().key)
                return mono(context) {
                    val deserializedRequest = getDeserializedPayload(payload)
                    logger.info(
                        "Incoming rsocket request -> ${deserializedRequest.third}: " +
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
                        "Outgoing rsocket response <- ${deserializedRequest.third}: " +
                            "payload: ${deserializedResponse.first}, " +
                            "request metadata: ${deserializedRequest.second}, " +
                            "response metadata: ${deserializedResponse.second}"
                    )
                    return@mono response
                }.contextCapture()
            }
        }
    }

    private fun getDeserializedPayload(payload: io.rsocket.Payload): Triple<Any, List<String>, String> {
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
}
