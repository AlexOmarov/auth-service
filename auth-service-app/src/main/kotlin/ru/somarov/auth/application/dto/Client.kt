package ru.somarov.auth.application.dto

import java.util.UUID

data class Client(
    val id: UUID,
    val email: String,
    val password: String
)
