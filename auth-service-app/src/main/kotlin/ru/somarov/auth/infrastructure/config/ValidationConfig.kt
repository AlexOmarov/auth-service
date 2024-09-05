package ru.somarov.auth.infrastructure.config

import io.ktor.server.plugins.requestvalidation.*
import ru.somarov.auth.presentation.request.ValidationRequest

fun setupValidation(config: RequestValidationConfig) {
    config.validate<ValidationRequest> { request ->
        if (request.token.isEmpty())
            ValidationResult.Invalid("A token must not be empty")
        else
            ValidationResult.Valid
    }
}
