package ru.somarov.auth.presentation

import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.ktor.server.rSocket
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import ru.somarov.auth.application.Service

internal fun Routing.auth(service: Service) {
    get("/") {
        service.makeWork()
        call.respondText("Ktor: hi")
    }
}

internal fun Routing.authSocket(service: Service) {
    rSocket("/") { RSocketRequestHandler { requestResponse { buildPayload { data(service.makeWork()) } } } }
}
