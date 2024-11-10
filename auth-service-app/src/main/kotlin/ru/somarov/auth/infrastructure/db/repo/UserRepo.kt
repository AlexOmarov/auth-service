package ru.somarov.auth.infrastructure.db.repo

import ru.somarov.auth.infrastructure.db.entity.User
import ru.somarov.auth.infrastructure.lib.db.DatabaseClient
import ru.somarov.auth.infrastructure.lib.util.generateRandomString

class UserRepo(private val client: DatabaseClient) {
    suspend fun findAll(): List<User> {
        return client.transactional() { connection -> listOf() }
    }

    fun save(user: User): String {
        return generateRandomString()
    }
}
