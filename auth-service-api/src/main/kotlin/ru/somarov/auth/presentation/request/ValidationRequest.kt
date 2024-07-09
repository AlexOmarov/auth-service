package ru.somarov.auth.presentation.request

import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.serialization.Serializable

@Serializable
@Schema(description = "Object which holds validation request data")
data class ValidationRequest(val token: String, val type: TokenType) {
    enum class TokenType {
        ACCESS, REFRESH, USER_ID
    }
}
