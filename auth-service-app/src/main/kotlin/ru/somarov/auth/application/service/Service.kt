package ru.somarov.auth.application.service

import io.ktor.util.logging.KtorSimpleLogger
import ru.somarov.auth.infrastructure.db.repo.ClientRepo
import ru.somarov.auth.infrastructure.db.repo.RevokedAuthorizationRepo
import ru.somarov.auth.presentation.request.ValidationRequest

class Service(
    private val clientRepo: ClientRepo,
    private val revokedAuthorizationRepo: RevokedAuthorizationRepo,
) {
    private val logger = KtorSimpleLogger(this.javaClass.name)

    suspend fun validate(request: ValidationRequest): Boolean {
        return true
    }
}
