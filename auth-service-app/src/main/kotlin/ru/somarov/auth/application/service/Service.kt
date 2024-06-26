package ru.somarov.auth.application.service

import io.ktor.util.logging.KtorSimpleLogger
import ru.somarov.auth.infrastructure.db.repo.ClientRepo

class Service(private val clientRepo: ClientRepo) {
    private val logger = KtorSimpleLogger(this.javaClass.name)

    suspend fun makeWork(message: String = "default"): String {
        val clients = clientRepo.getAll()
        clients.forEach { logger.info("Hi, ${it.email}") }
        logger.info("GOT MSG $message")
        return "done"
    }
}
