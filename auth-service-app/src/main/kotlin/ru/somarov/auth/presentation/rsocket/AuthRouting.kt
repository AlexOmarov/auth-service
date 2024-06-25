package ru.somarov.auth.presentation.rsocket

import io.ktor.server.routing.Routing
import io.ktor.utils.io.core.writeText
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.ktor.server.rSocket
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import ru.somarov.auth.application.service.Service

internal fun Routing.authSocket(service: Service) {
    rSocket("/") {
        RSocketRequestHandler {
            requestResponse {
                buildPayload {
                    data { writeText(service.makeWork()) }
                }
            }
        }
    }
}
