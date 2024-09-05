package ru.somarov.auth.application.service

import ru.somarov.auth.infrastructure.db.repo.ClientRepo
import ru.somarov.auth.infrastructure.db.repo.RevokedAuthorizationRepo
import ru.somarov.auth.presentation.request.ValidationRequest

class Service(
    private val clientRepo: ClientRepo,
    private val revokedAuthorizationRepo: RevokedAuthorizationRepo,
) {

    suspend fun validate(request: ValidationRequest): Boolean {
        return true
    }
}
