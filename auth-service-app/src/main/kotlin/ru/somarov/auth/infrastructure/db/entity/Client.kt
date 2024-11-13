package ru.somarov.auth.infrastructure.db.entity

import io.r2dbc.spi.Row

data class Client(
    val id: String,
    val redirectUris: List<String>
) {
    companion object {
        @Suppress("kotlin:S6518", "UNCHECKED_CAST") // Cannot use [] due to r2dbc api
        fun map(row: Row): Client {
            return Client(
                row.get("id", String::class.java)!!,
                row.get("redirect_uris", List::class.java)!! as List<String>,
            )
        }
    }
}
