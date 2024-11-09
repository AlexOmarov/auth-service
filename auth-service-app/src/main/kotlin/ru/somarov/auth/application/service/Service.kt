package ru.somarov.auth.application.service

import kotlinx.datetime.Clock.System.now
import ru.somarov.auth.infrastructure.db.entity.Client
import ru.somarov.auth.infrastructure.db.repo.ClientRepo
import ru.somarov.auth.infrastructure.lib.kafka.Producer
import ru.somarov.auth.infrastructure.lib.oid.JwtService
import ru.somarov.auth.infrastructure.lib.util.generateRandomString
import ru.somarov.auth.presentation.event.Metadata
import ru.somarov.auth.presentation.event.broadcast.RegistrationBroadcast
import ru.somarov.auth.presentation.request.RegistrationRequest
import ru.somarov.auth.presentation.request.ValidationRequest

class Service(
    private val clientRepo: ClientRepo,
    private val jwtService: JwtService,
    private val producer: Producer<RegistrationBroadcast>
) {

    fun validate(token: String, type: ValidationRequest.TokenType): Boolean {
        return jwtService.verify(token, getTokenType(type)) != null
    }

    suspend fun register(request: RegistrationRequest): String {
        val id = clientRepo.save(Client(generateRandomString(), request.email, request.password))
        producer.send(RegistrationBroadcast(id, now()), Metadata(now(), generateRandomString(), 0))
        return id
    }

    private fun getTokenType(type: ValidationRequest.TokenType): JwtService.KeyType {
        return when (type) {
            ValidationRequest.TokenType.ACCESS -> JwtService.KeyType.ACCESS
            ValidationRequest.TokenType.REFRESH -> JwtService.KeyType.REFRESH
            ValidationRequest.TokenType.USER_ID -> JwtService.KeyType.USERID
        }
    }
}
