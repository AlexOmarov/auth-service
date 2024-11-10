package ru.somarov.auth.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock.System.now
import ru.somarov.auth.infrastructure.db.entity.User
import ru.somarov.auth.infrastructure.db.repo.UserRepo
import ru.somarov.auth.infrastructure.lib.kafka.Producer
import ru.somarov.auth.infrastructure.lib.util.generateRandomString
import ru.somarov.auth.presentation.event.Metadata
import ru.somarov.auth.presentation.event.broadcast.RegistrationBroadcast
import ru.somarov.auth.presentation.request.RegistrationRequest

class RegistrationService(private val repo: UserRepo, private val producer: Producer<RegistrationBroadcast>) {

    private val logger = KotlinLogging.logger { }

    suspend fun register(request: RegistrationRequest): String {
        logger.info { "Got registration request for new user ${request.email}" }

        val user = User(generateRandomString(), request.tenantId, request.email, request.password)
        val id = repo.save(user)
        logger.info { "Registered user ${request.email}" }

        val event = RegistrationBroadcast(id, now())
        producer.send(event, Metadata(generateRandomString()))
        logger.info { "Produced registration event for user ${request.email}" }

        return id
    }
}
