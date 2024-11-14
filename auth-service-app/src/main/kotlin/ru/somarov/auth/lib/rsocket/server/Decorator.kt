package ru.somarov.auth.lib.rsocket.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.micrometer.observation.ObservationRegistry
import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.payload.Payload
import io.rsocket.metadata.WellKnownMimeType
import io.rsocket.micrometer.MicrometerRSocketInterceptor
import io.rsocket.micrometer.observation.ObservationResponderRSocketProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.reactor.awaitSingle
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.somarov.auth.lib.observability.ObservabilityRegistry
import ru.somarov.auth.lib.observability.micrometer.observeSuspendedAsMono
import ru.somarov.auth.lib.util.deserialize
import ru.somarov.auth.lib.util.toJavaPayload
import ru.somarov.auth.lib.util.toKotlinPayload
import kotlin.coroutines.CoroutineContext

class Decorator(private val input: RSocket, private val registry: ObservationRegistry) : io.rsocket.RSocket {
    private val logger = logger { }
    private val mapper = ObjectMapper(CBORFactory()).registerKotlinModule()

    @ExperimentalMetadataApi
    override fun requestResponse(payload: io.rsocket.Payload): Mono<io.rsocket.Payload> {
        val context = (Dispatchers.IO + input.coroutineContext).minusKey(Job().key)
        val observation = registry.currentObservation!!

        return observation.observeSuspendedAsMono(coroutineContext = context) {
            val req = payload.deserialize(mapper)
            logger.info {
                "Incoming rsocket request -> ${req.metadata[WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.string]}: " +
                    "payload: ${req.body}, metadata: ${req.metadata}"
            }

            val result = input.requestResponse(payload.toKotlinPayload()).toJavaPayload()

            val resp = result.deserialize(mapper)
            logger.info {
                "Outgoing rsocket response <- ${resp.metadata[WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.string]}: " +
                    "payload: ${resp.body}, metadata: ${resp.metadata}"
            }

            result
        }.contextCapture()
    }

    @ExperimentalMetadataApi
    override fun requestStream(payload: io.rsocket.Payload): Flux<io.rsocket.Payload> {
        val context = (Dispatchers.IO + input.coroutineContext).minusKey(Job().key)

        val req = payload.deserialize(mapper)

        logger.info {
            "Incoming rsocket request -> ${req.metadata[WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.string]}: " +
                "payload: ${req.body}, metadata: ${req.metadata}"
        }

        val result = input.requestStream(payload.toKotlinPayload())

        return result.map {
            val payload = it.toJavaPayload()
            val resp = payload.deserialize(mapper)
            logger.info {
                "Outgoing rsocket response <- ${resp.metadata[WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.string]}: " +
                    "payload: ${resp.body}, metadata: ${resp.metadata}"
            }
            payload
        }.asFlux(context)
    }

    companion object {
        @ExperimentalMetadataApi
        fun decorate(
            input: RSocket,
            registry: ObservabilityRegistry
        ): RSocket {
            val enrichedJavaRSocket = ObservationResponderRSocketProxy(
                @Suppress
                MicrometerRSocketInterceptor(registry.meterRegistry)
                    .apply(Decorator(input, registry.observationRegistry)),
                registry.observationRegistry
            )
            return object : RSocket {
                override val coroutineContext: CoroutineContext
                    get() = input.coroutineContext

                override suspend fun requestResponse(payload: Payload): Payload {
                    return enrichedJavaRSocket.requestResponse(payload.toJavaPayload())
                        .contextCapture()
                        .awaitSingle()
                        .toKotlinPayload()
                }

                override fun requestStream(payload: Payload): Flow<Payload> {
                    return enrichedJavaRSocket.requestStream(payload.toJavaPayload())
                        .contextCapture()
                        .map { it.toKotlinPayload() }
                        .asFlow()
                }
            }
        }
    }
}
