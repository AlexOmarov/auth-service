package ru.somarov.auth.infrastructure.otel

import io.ktor.server.application.ApplicationCall
import io.micrometer.observation.transport.ReceiverContext

class ApplicationCallReceiverContext : ReceiverContext<ApplicationCall>({ c, k -> c.request.headers[k] })
