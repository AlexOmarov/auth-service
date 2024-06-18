package ru.somarov.auth.infrastructure.rsocket

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readBytes
import io.micrometer.observation.ObservationRegistry
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.core.Interceptor
import io.rsocket.kotlin.payload.Payload
import io.rsocket.micrometer.observation.ObservationResponderRSocketProxy
import io.rsocket.util.DefaultPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.flux
import kotlinx.coroutines.reactor.mono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.coroutines.CoroutineContext

class ServerTracingInterceptor(private val registry: ObservationRegistry) : Interceptor<RSocket> {

    override fun intercept(input: RSocket): RSocket {
        val proxy = ObservationResponderRSocketProxy(getRsocketWrapper(input), registry)
        return object : RSocket {
            override val coroutineContext: CoroutineContext
                get() = input.coroutineContext

            override suspend fun fireAndForget(payload: Payload) {
                proxy.fireAndForget(DefaultPayload.create(payload.data.readBytes(), payload.metadata?.readBytes()))
            }

            override suspend fun requestResponse(payload: Payload): Payload {
                val result = proxy.requestResponse(
                    DefaultPayload.create(
                        payload.data.readBytes(),
                        payload.metadata?.readBytes()
                    )
                ).awaitSingle()
                return Payload(
                    ByteReadChannel(result.data).readRemaining(),
                    ByteReadChannel(result.metadata).readRemaining()
                )
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

    private fun getRsocketWrapper(input: RSocket): io.rsocket.RSocket {
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
}
