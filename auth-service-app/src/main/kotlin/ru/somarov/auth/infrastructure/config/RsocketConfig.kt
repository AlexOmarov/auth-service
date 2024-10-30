package ru.somarov.auth.infrastructure.config

import io.rsocket.kotlin.core.RSocketServerBuilder
import ru.somarov.auth.infrastructure.lib.observability.ObservabilityRegistry
import ru.somarov.auth.infrastructure.lib.rsocket.server.Interceptor

fun setupRSocketServer(
    builder: RSocketServerBuilder,
    registry: ObservabilityRegistry
) {
    builder.interceptors {
        forResponder(Interceptor(registry))
    }
}
