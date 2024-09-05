package ru.somarov.auth.infrastructure.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationRegistry
import io.rsocket.kotlin.core.RSocketServerBuilder
import ru.somarov.auth.infrastructure.rsocket.server.Interceptor

fun setupRSocketServer(
    builder: RSocketServerBuilder,
    meterRegistry: MeterRegistry,
    observationRegistry: ObservationRegistry
) {
    builder.interceptors {
        forResponder(Interceptor(meterRegistry, observationRegistry))
    }
}
