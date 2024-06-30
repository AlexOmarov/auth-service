package ru.somarov.auth.infrastructure.config

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationRegistry
import io.rsocket.kotlin.ktor.server.RSocketSupport

fun setupRsocket(application: Application, meterRegistry: MeterRegistry, observationRegistry: ObservationRegistry) {
    application.install(WebSockets)
    application.install(RSocketSupport) {
        server {
            interceptors {
                // forResponder(ServerObservabilityInterceptor(meterRegistry, observationRegistry))
            }
        }
    }
}
