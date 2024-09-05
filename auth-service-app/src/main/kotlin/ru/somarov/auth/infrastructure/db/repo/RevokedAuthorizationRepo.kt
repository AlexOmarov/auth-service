package ru.somarov.auth.infrastructure.db.repo

import ru.somarov.auth.infrastructure.db.DatabaseClient
import ru.somarov.auth.infrastructure.db.entity.RevokedAuthorization
import java.util.UUID

class RevokedAuthorizationRepo(private val client: DatabaseClient) {
    suspend fun findAll(): List<RevokedAuthorization> {
        return client.transactional("Select * from revoked_authorization", mapOf()) { row, _ ->
            @Suppress("kotlin:S6518") // Cannot use [] due to r2dbc api
            RevokedAuthorization(
                row.get("id", UUID::class.java)!!,
                row.get("access", String::class.java)!!,
                row.get("refresh", String::class.java)!!,
            )
        }
    }
}
