package ru.somarov.auth.infrastructure.db.entity

import io.r2dbc.spi.Row

data class RevokedAuthorization(
    val id: String,
    val token: String,
    val clientId: String
) {
    companion object {
        @Suppress("kotlin:S6518") // Cannot use [] due to r2dbc api
        fun map(row: Row): RevokedAuthorization {
            return RevokedAuthorization(
                row.get("id", String::class.java)!!,
                row.get("access", String::class.java)!!,
                row.get("refresh", String::class.java)!!,
            )
        }
    }
}
