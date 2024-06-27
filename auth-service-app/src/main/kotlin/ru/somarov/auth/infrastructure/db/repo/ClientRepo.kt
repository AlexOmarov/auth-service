package ru.somarov.auth.infrastructure.db.repo

import ru.somarov.auth.application.dto.Client
import ru.somarov.auth.infrastructure.db.DatabaseClient
import java.util.UUID

class ClientRepo(private val client: DatabaseClient) {
    suspend fun findAll(): List<Client> {
        return client.transactional("Select * from client", mapOf()) { row, _ ->
            @Suppress("kotlin:S6518") // Cannot use [] due to r2dbc api
            Client(
                row.get("id", UUID::class.java)!!,
                row.get("email", String::class.java)!!,
                row.get("password", String::class.java)!!,
            )
        }
    }
}
