package ru.somarov.auth.presentation.response

import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.serialization.Serializable
import ru.somarov.auth.presentation.response.dto.Authorization

@Serializable
@Schema(description = "Object which holds response data for authorization request")
data class AuthorizationResponse(val authorization: Authorization)
