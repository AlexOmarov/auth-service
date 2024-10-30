package ru.somarov.auth.infrastructure.db.repo

import ru.somarov.auth.infrastructure.db.entity.Client
import ru.somarov.auth.infrastructure.lib.db.DatabaseClient
import ru.somarov.auth.infrastructure.lib.util.generateRandomString

class ClientRepo(private val client: DatabaseClient) {
    suspend fun findAll(): List<Client> {
        return client.transactional("Select * from client", mapOf()) { row, _ -> Client.map(row) }
    }

    fun save(client: Client): String {
        return generateRandomString()
    }
}
