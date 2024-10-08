package ru.somarov.auth.presentation.http

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get

internal fun Routing.healthcheck() {
    get("health") {
        logger { }. info { "Incoming health request" }
        call.respondText("UP")
    }
}
