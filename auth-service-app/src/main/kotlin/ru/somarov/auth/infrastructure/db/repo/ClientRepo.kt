package ru.somarov.auth.infrastructure.db.repo

import ru.somarov.auth.infrastructure.db.DatabaseClient
import ru.somarov.auth.infrastructure.db.entity.Client
import java.util.*

class ClientRepo(private val client: DatabaseClient) {
    suspend fun findAll(): List<Client> {
        return client.transactional("Select * from client", mapOf()) { row, _ -> Client.map(row) }
    }

    fun save(client: Client): String {
        return UUID.randomUUID().toString()
    }
}
