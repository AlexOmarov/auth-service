package ru.somarov.auth.infrastructure.db

import ru.somarov.auth.application.dto.Client

class ClientRepo(private val client: DatabaseClient) {
    suspend fun getAll(): List<Client> {
        return client
            .executeQuery("Select * from client", mapOf()) { row -> Client.parse(row) }
            .toList()
    }
}
