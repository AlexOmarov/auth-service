package ru.somarov.auth.presentation.rsocket

import io.ktor.server.routing.Routing
import io.ktor.utils.io.core.writeText
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.ktor.server.rSocket
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.somarov.auth.application.service.Service

internal fun Routing.authSocket(service: Service) {
    rSocket("login") {
        RSocketRequestHandler {
            requestResponse {
                buildPayload {
                    data {
                        writeText(Json.Default.encodeToString(service.makeWork()))
                    }
                }
            }
        }
    }
}
