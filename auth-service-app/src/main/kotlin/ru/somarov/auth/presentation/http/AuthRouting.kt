package ru.somarov.auth.presentation.http

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import ru.somarov.auth.application.service.AuthenticationService

internal fun Routing.healthcheck() {
    get("health") {
        logger { }.info { "Incoming health request" }
        call.respondText("UP")
    }
}

internal fun Routing.auth(authenticationService: AuthenticationService) {
    get("authorize") {
        val redirectPath = authenticationService.authorize(call.request.queryParameters)
        call.respondRedirect(redirectPath)
    }
    post("token") {
        call.parameters
    }
}
