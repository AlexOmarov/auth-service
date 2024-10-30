package ru.somarov.auth.infrastructure.db.repo

import ru.somarov.auth.infrastructure.lib.db.DatabaseClient
import ru.somarov.auth.infrastructure.db.entity.RevokedAuthorization

class RevokedAuthorizationRepo(private val client: DatabaseClient) {
    suspend fun findAll(): List<RevokedAuthorization> {
        return client.transactional("Select * from revoked_authorization", mapOf()) { row, _ ->
            RevokedAuthorization.map(row)
        }
    }
}
