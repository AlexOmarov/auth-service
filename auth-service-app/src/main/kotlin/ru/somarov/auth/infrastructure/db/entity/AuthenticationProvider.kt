package ru.somarov.auth.infrastructure.db.entity

import java.util.UUID

data class AuthenticationProvider(
    val id: UUID,
    val code: String
)
