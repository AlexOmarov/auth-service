package ru.somarov.auth.infrastructure.rsocket

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationRegistry
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.core.Interceptor
import io.rsocket.kotlin.payload.Payload
import kotlinx.coroutines.reactor.awaitSingle
import kotlin.coroutines.CoroutineContext

internal class ServerInterceptor(
    private val meterRegistry: MeterRegistry,
    private val observationRegistry: ObservationRegistry
) : Interceptor<RSocket> {

    override fun intercept(input: RSocket): RSocket {
        val proxy = ServerWrapper.create(input, observationRegistry, meterRegistry)

        return object : RSocket {
            override val coroutineContext: CoroutineContext
                get() = input.coroutineContext

            override suspend fun requestResponse(payload: Payload): Payload {
                return PayloadWrapper(
                    proxy.requestResponse(PayloadWrapper.fromKotlinPayload(payload)).contextCapture().awaitSingle()
                ).toKotlinPayload()
            }
        }
    }
}
