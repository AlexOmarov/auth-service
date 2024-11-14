package ru.somarov.auth.infrastructure.db.repo

import ru.somarov.auth.infrastructure.db.entity.Client
import ru.somarov.auth.lib.db.DatabaseClient
import ru.somarov.auth.lib.util.generateRandomString

class ClientRepo(private val client: DatabaseClient) {
    suspend fun findAll(): List<Client> {
        return client.transactional() { connection -> listOf() }
    }

    fun save(user: Client): String {
        return generateRandomString()
    }
}
