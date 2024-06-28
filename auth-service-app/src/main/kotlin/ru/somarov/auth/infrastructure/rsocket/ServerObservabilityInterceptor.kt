package ru.somarov.auth.infrastructure.rsocket

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.flux
import kotlinx.coroutines.reactor.mono
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import reactor.core.publisher.Flux
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

            override suspend fun fireAndForget(payload: Payload) {
                proxy.fireAndForget(DefaultPayload.create(payload.data.readBytes(), payload.metadata?.readBytes()))
            }

            override suspend fun requestResponse(payload: Payload): Payload {

                val deserializedRequest = getDeserializedPayload(payload)
                logger.info(
                    "Incoming rsocket request -> ${deserializedRequest.third}: " +
                        "payload: ${deserializedRequest.first}, metadata: ${deserializedRequest.second}"
                )

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

                val deserializedResponse = getDeserializedPayload(response)
                logger.info(
                    "Outgoing rsocket response <- ${deserializedRequest.third}: " +
                        "payload: ${deserializedResponse.first}, " +
                        "request metadata: ${deserializedRequest.second}, " +
                        "response metadata: ${deserializedResponse.second}"
                )
                return response
            }

            override fun requestStream(payload: Payload): Flow<Payload> {
                return proxy.requestStream(
                    DefaultPayload.create(
                        payload.data.readBytes(),
                        payload.metadata?.readBytes()
                    )
                ).asFlow().map {
                    Payload(
                        ByteReadChannel(it.data).readRemaining(),
                        ByteReadChannel(it.metadata).readRemaining()
                    )
                }
            }
        }
    }

    private fun getRSocketWrapper(input: RSocket): io.rsocket.RSocket {
        return object : io.rsocket.RSocket {
            @Suppress("kotlin:S6508") // Reactor java Void type
            override fun fireAndForget(payload: io.rsocket.Payload): Mono<Void> {
                return mono(input.coroutineContext) {
                    input.fireAndForget(
                        Payload(
                            ByteReadChannel(payload.data).readRemaining(),
                            ByteReadChannel(payload.metadata).readRemaining()
                        )
                    )
                    return@mono null
                }
            }

            override fun requestResponse(payload: io.rsocket.Payload): Mono<io.rsocket.Payload> {
                return mono(input.coroutineContext) {
                    val result = input.requestResponse(
                        Payload(
                            ByteReadChannel(payload.data).readRemaining(),
                            ByteReadChannel(payload.metadata).readRemaining()
                        )
                    )
                    return@mono DefaultPayload.create(result.data.readBytes(), result.metadata?.readBytes())
                }
            }

            override fun requestStream(payload: io.rsocket.Payload): Flux<io.rsocket.Payload> {
                return flux(input.coroutineContext) {
                    input.requestStream(
                        Payload(
                            ByteReadChannel(payload.data).readRemaining(),
                            ByteReadChannel(payload.metadata).readRemaining()
                        )
                    )
                        .asFlux()
                }
            }
        }
    }

    private fun getDeserializedPayload(payload: Payload): Triple<Any, List<String>, String> {
        val data = try {
            val array = payload.data.copy().readBytes()
            if (array.isNotEmpty()) {
                Json.decodeFromString<String>(String(array))
            } else {
                "Body is null"
            }
        } catch (e: SerializationException) {
            logger.error("Got error while deserializing json to string", e)
            "Body is null"
        }
        var routing = "null"
        val metadata = payload.metadata?.copy()?.let {
            CompositeMetadata(Unpooled.wrappedBuffer(it.readBytes()), false)
                .map { met ->
                    val content = if (isText(met.content, encoding)) met.content.toString(encoding) else "Not text"
                    if (met.mimeType == WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.string) {
                        routing = if (content.isNotEmpty()) content.substring(1) else content
                        "Header(mime: ${met.mimeType}, content: $routing)"
                    } else {
                        "Header(mime: ${met.mimeType}, content: $content)"
                    }
                }
        } ?: listOf()

        return Triple(data, metadata, routing)
    }
}
