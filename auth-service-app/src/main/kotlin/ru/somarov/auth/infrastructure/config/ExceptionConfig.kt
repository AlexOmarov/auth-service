package ru.somarov.auth.infrastructure.config

import io.ktor.http.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import ru.somarov.auth.presentation.response.ErrorResponse

fun setupExceptionHandling(config: StatusPagesConfig) {
    config.exception<Throwable> { call, cause ->
        if (cause is RequestValidationException) {
            call.respond(HttpStatusCode.BadRequest, cause.reasons.joinToString())
        } else {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(mapOf("cause" to (cause.message ?: "undefined")))
            )
        }
    }
}
