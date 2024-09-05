package ru.somarov.auth.infrastructure.db.entity

import java.util.UUID

data class Client(
    val id: UUID,
    val email: String,
    val password: String
)
