package ru.somarov.auth.application.service

import kotlinx.datetime.Clock.System.now
import ru.somarov.auth.infrastructure.db.entity.Client
import ru.somarov.auth.infrastructure.db.repo.ClientRepo
import ru.somarov.auth.infrastructure.db.repo.RevokedAuthorizationRepo
import ru.somarov.auth.infrastructure.lib.kafka.Producer
import ru.somarov.auth.infrastructure.lib.util.generateRandomString
import ru.somarov.auth.presentation.event.Metadata
import ru.somarov.auth.presentation.event.broadcast.RegistrationBroadcast
import ru.somarov.auth.presentation.request.RegistrationRequest
import ru.somarov.auth.presentation.request.ValidationRequest

class Service(
    private val clientRepo: ClientRepo,
    private val producer: Producer<RegistrationBroadcast>,
    private val revokedAuthorizationRepo: RevokedAuthorizationRepo,
) {

    suspend fun validate(request: ValidationRequest): Boolean {
        return true
    }

    suspend fun register(request: RegistrationRequest): String {
        val id = clientRepo.save(Client(generateRandomString(), request.email, request.password ?: ""))
        producer.send(RegistrationBroadcast(id, now()), Metadata(now(), generateRandomString(), 0))
        return id
    }
}
