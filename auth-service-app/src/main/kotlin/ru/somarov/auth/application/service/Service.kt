package ru.somarov.auth.application.service

import io.ktor.util.logging.KtorSimpleLogger
import ru.somarov.auth.infrastructure.db.repo.ClientRepo
import ru.somarov.auth.infrastructure.db.repo.RevokedAuthorizationRepo
import ru.somarov.auth.presentation.response.AuthorizationResponse
import ru.somarov.auth.presentation.response.dto.Authorization

class Service(
    private val clientRepo: ClientRepo,
    private val revokedAuthorizationRepo: RevokedAuthorizationRepo,
) {
    private val logger = KtorSimpleLogger(this.javaClass.name)

    suspend fun makeWork(message: String = "default"): AuthorizationResponse {
        /*val clients = clientRepo.findAll()
        val revokedAuthorizations = revokedAuthorizationRepo.findAll()
        clients.forEach { logger.info("Hi, ${it.email}") }*/
        /*logger.info("Revoked, $revokedAuthorizations")*/
        logger.info("GOT MSG $message")
        return AuthorizationResponse(Authorization("111", "123"))
    }
}
