package ru.somarov.auth.infrastructure.lib.rsocket.server

import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.core.Interceptor
import ru.somarov.auth.infrastructure.lib.observability.ObservabilityRegistry

internal class Interceptor(
    private val registry: ObservabilityRegistry
) : Interceptor<RSocket> {

    @ExperimentalMetadataApi
    override fun intercept(input: RSocket): RSocket {
        return Decorator.decorate(input, registry)
    }
}
