package ru.somarov.auth.infrastructure.db.entity

import io.r2dbc.spi.Row

data class RevokedToken(
    val id: String,
    val token: String
) {
    companion object {
        @Suppress("kotlin:S6518") // Cannot use [] due to r2dbc api
        fun map(row: Row) = RevokedToken(
            row.get("id", String::class.java)!!,
            row.get("token", String::class.java)!!
        )
    }
}
