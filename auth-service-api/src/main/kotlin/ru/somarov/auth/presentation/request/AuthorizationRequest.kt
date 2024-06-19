package ru.somarov.auth.presentation.request

import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.serialization.Serializable
import ru.somarov.auth.serialization.UUIDSerializer
import java.util.UUID

@Serializable
@Schema(description = "Object which holds details of authorization")
data class AuthorizationRequest(
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID,
    val authorization: Authorization
)
