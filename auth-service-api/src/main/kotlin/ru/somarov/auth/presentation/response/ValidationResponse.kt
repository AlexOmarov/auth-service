package ru.somarov.auth.presentation.response

import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.serialization.Serializable

@Serializable
@Schema(description = "Object which holds response data for token validation operation")
data class ValidationResponse(val isValid: Boolean)
