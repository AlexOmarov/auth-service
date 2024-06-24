package ru.somarov.auth.application

import io.ktor.util.logging.KtorSimpleLogger
import ru.somarov.auth.infrastructure.db.ClientRepo

class Service(private val clientRepo: ClientRepo) {
    private val logger = KtorSimpleLogger(this.javaClass.name)

    suspend fun makeWork(): String {
        val clients = clientRepo.getAll()
        clients.forEach { logger.info("Hi, ${it.email}") }
        return "done"
    }
}
