package ru.somarov.auth.application.dto

import java.util.UUID

data class RevokedAuthorization(
    val id: UUID,
    val access: String,
    val refresh: String
)
