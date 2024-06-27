package ru.somarov.auth.infrastructure.db.repo

import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import reactor.core.publisher.Mono
import ru.somarov.auth.application.dto.Client
import ru.somarov.auth.infrastructure.db.DatabaseClient
import java.util.UUID

class ClientRepo(private val client: DatabaseClient) {
    suspend fun findAll(): List<Client> {
        return client
            .execute("Select * from client", mapOf()) { result ->
                Mono.just(
                    result
                        .asFlow()
                        .map {
                            it.map { row, _ ->
                                @Suppress("kotlin:S6518") // Cannot use [] due to r2dbc api
                                Client(
                                    row.get("id", UUID::class.java)!!,
                                    row.get("email", String::class.java)!!,
                                    row.get("password", String::class.java)!!,
                                )
                            }.awaitFirstOrNull()
                        }.filterNotNull().toList()
                )
            }
    }
}
