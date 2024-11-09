package ru.somarov.auth.presentation.request

import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.serialization.Serializable

@Serializable
@Schema(description = "Object which holds registration request data")
data class RegistrationRequest(
    val email: String,
    val password: String
)
