package ru.somarov.auth.infrastructure.config.otel.context

import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.ApplicationResponse
import io.ktor.server.response.header
import io.micrometer.observation.transport.Kind
import io.micrometer.observation.transport.RequestReplySenderContext

class ApplicationCallSenderContext : RequestReplySenderContext<ApplicationCall, ApplicationResponse>(
    { c, k, v -> c?.response?.header(k, v) },
    Kind.SERVER
)
