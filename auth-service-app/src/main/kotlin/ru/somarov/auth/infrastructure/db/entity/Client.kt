package ru.somarov.auth.infrastructure.db.entity

import io.r2dbc.spi.Row

data class Client(
    val id: String,
    val email: String,
    val password: String
) {
    companion object {
        @Suppress("kotlin:S6518") // Cannot use [] due to r2dbc api
        fun map(row: Row): Client {
            return Client(
                row.get("id", String::class.java)!!,
                row.get("email", String::class.java)!!,
                row.get("password", String::class.java)!!,
            )
        }
    }
}
