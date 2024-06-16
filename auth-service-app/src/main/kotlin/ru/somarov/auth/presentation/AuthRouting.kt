package ru.somarov.auth.presentation

import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import ru.somarov.auth.application.Service

internal fun Routing.auth() {
    val env = environment!!
    get("/") {
        Service(env).makeWork()
        call.respondText("Ktor: dgh hi")
    }
}
