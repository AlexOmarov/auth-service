package ru.somarov.auth.application.dto

import io.r2dbc.spi.Row
import java.util.UUID

data class Client(
    val id: UUID,
    val email: String,
    val password: String
) {
    @Suppress("kotlin:S6518") // Cannot use [] syntax because of second param
    companion object {
        fun parse(row: Row): Client {
            return Client(
                row.get("id", UUID::class.java)!!,
                row.get("email", String::class.java)!!,
                row.get("password", String::class.java)!!,
            )
        }
    }
}
