package ru.somarov.auth.presentation.http

import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import ru.somarov.auth.application.Service

internal fun Routing.auth(service: Service) {
    get("/") {
        service.makeWork()
        call.respondText("Ktor: hi")
    }
}
