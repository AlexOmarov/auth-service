package ru.somarov.auth.infrastructure.config

import io.ktor.serialization.kotlinx.cbor.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.rsocket.kotlin.ktor.server.RSocketSupport
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import ru.somarov.auth.application.service.Service
import ru.somarov.auth.infrastructure.db.DatabaseClient
import ru.somarov.auth.infrastructure.db.repo.ClientRepo
import ru.somarov.auth.infrastructure.db.repo.RevokedAuthorizationRepo
import ru.somarov.auth.infrastructure.kafka.Producer
import ru.somarov.auth.infrastructure.props.AppProps
import ru.somarov.auth.presentation.event.broadcast.RegistrationBroadcast
import ru.somarov.auth.presentation.http.healthcheck
import ru.somarov.auth.presentation.rsocket.authSocket
import java.util.*

@Suppress("unused") // Referenced in application.yaml
@ExperimentalSerializationApi
internal fun Application.config() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    val props = AppProps.parse(environment)
    val cbor = Cbor { ignoreUnknownKeys = true }
    val (meterRegistry, observationRegistry) = setupObservability(props)
    val dbClient = DatabaseClient(props, meterRegistry, observationRegistry)
    val clientRepo = ClientRepo(dbClient)
    val revokedAuthorizationRepo = RevokedAuthorizationRepo(dbClient)
    val registrationBroadcastProducer = Producer(
        Producer.ProducerProps(
            props.kafka.brokers,
            props.kafka.producers.registration.maxInFlight,
            props.kafka.producers.registration.topic
        ), observationRegistry, RegistrationBroadcast::class.java
    )
    val service = Service(clientRepo, registrationBroadcastProducer, revokedAuthorizationRepo)
    setupScheduler(dbClient.factory, observationRegistry, environment)

    install(ContentNegotiation) { cbor(cbor) }
    install(RequestValidation) { setupValidation(this) }
    install(StatusPages) { setupExceptionHandling(this) }
    install(MicrometerMetrics) { setupMetrics(this, meterRegistry) }
    install(WebSockets)
    install(RSocketSupport) { server { setupRSocketServer(this, meterRegistry, observationRegistry) } }

    routing {
        healthcheck()
        authSocket(service)
    }
}
