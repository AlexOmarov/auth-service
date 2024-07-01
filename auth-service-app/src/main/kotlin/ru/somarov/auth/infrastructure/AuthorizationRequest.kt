package ru.somarov.auth.infrastructure

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AuthorizationRequest(
    @Serializable(with = UUIDSerializer::class)
    val userId: UUID
)
