package ru.somarov.auth.infrastructure.config

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json
import ru.somarov.auth.presentation.request.AuthorizationRequest
import ru.somarov.auth.presentation.response.ErrorResponse
import java.util.UUID

fun setupPipeline(application: Application) {
    application.install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }

    application.install(RequestValidation) {
        validate<AuthorizationRequest> { request ->
            if (request.userId == UUID.randomUUID())
                ValidationResult.Invalid("A customer ID should be greater than 0")
            else
                ValidationResult.Valid
        }
    }

    application.install(StatusPages) {
        exception<Throwable> { call, cause ->
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
}
