package ru.somarov.auth.infrastructure.db.entity

import io.r2dbc.spi.Row

data class User(
    val id: String,
    val tenantId: String,
    val email: String,
    val password: String,
) {
    companion object {
        @Suppress("kotlin:S6518") // Cannot use [] due to r2dbc api
        fun map(row: Row): User {
            return User(
                row.get("id", String::class.java)!!,
                row.get("tenant_id", String::class.java)!!,
                row.get("email", String::class.java)!!,
                row.get("password", String::class.java)!!,
            )
        }
    }
}
