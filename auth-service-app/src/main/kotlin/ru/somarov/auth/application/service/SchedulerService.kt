package ru.somarov.auth.application.service

import io.ktor.util.logging.KtorSimpleLogger
import ru.somarov.auth.infrastructure.db.repo.ClientRepo

class SchedulerService(private val clientRepo: ClientRepo) {
    private val logger = KtorSimpleLogger(this.javaClass.name)

    suspend fun makeWorkInScheduler(): String {
        val clients = clientRepo.findAll()
        clients.forEach { logger.info("Hi, ${it.email}") }
        return "done"
    }
}
