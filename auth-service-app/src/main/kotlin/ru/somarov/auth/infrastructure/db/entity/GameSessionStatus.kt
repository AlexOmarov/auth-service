package ru.somarov.auth.infrastructure.db.entity

import java.util.UUID

data class GameSessionStatus(
    val id: UUID,
    val code: String
)
