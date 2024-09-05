package ru.somarov.auth.infrastructure.rsocket

import io.ktor.util.logging.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationRegistry
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.payload.Payload
import io.rsocket.micrometer.MicrometerRSocketInterceptor
import io.rsocket.micrometer.observation.ObservationResponderRSocketProxy
import io.rsocket.util.DefaultPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import reactor.core.publisher.Mono
import ru.somarov.auth.infrastructure.observability.micrometer.observeSuspendedMono

class ServerWrapper {
    companion object {
        private val logger = KtorSimpleLogger(this.javaClass.name)
        fun create(input: RSocket, observationRegistry: ObservationRegistry, meterRegistry: MeterRegistry): io.rsocket.RSocket {
            val wrapper = getRSocketWrapper(input, observationRegistry)
            val measuredRSocket: io.rsocket.RSocket = MicrometerRSocketInterceptor(meterRegistry).apply(wrapper)
            return ObservationResponderRSocketProxy(measuredRSocket, observationRegistry)
        }

        private fun getRSocketWrapper(input: RSocket, observationRegistry: ObservationRegistry): io.rsocket.RSocket {
            return object : io.rsocket.RSocket {
                override fun requestResponse(payload: io.rsocket.Payload): Mono<io.rsocket.Payload> {
                    val context = (Dispatchers.IO + input.coroutineContext).minusKey(Job().key)
                    val observation = observationRegistry.currentObservation!!
                    return observation.observeSuspendedMono(coroutineContext = context) {
                        val deserializedRequest = PayloadWrapper(payload).deserialize()
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

                        val deserializedResponse = PayloadWrapper(response).deserialize()
                        logger.info(
                            "Outgoing rsocket response <- ${deserializedRequest.third}: " +
                                    "payload: ${deserializedResponse.first}, " +
                                    "request metadata: ${deserializedRequest.second}, " +
                                    "response metadata: ${deserializedResponse.second}"
                        )
                        return@observeSuspendedMono response
                    }.contextCapture()
                }
            }
        }
    }
}