package ru.somarov.auth.infrastructure

import io.ktor.serialization.kotlinx.cbor.*
import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import ru.somarov.auth.application.service.Service
import ru.somarov.auth.infrastructure.db.repo.ClientRepo
import ru.somarov.auth.infrastructure.lib.db.ConnectionFactoryFactory
import ru.somarov.auth.infrastructure.lib.db.DatabaseClient
import ru.somarov.auth.infrastructure.lib.oid.JwtService
import ru.somarov.auth.infrastructure.lib.kafka.Producer
import ru.somarov.auth.infrastructure.lib.observability.ObservabilityRegistryFactory
import ru.somarov.auth.presentation.event.broadcast.RegistrationBroadcast
import ru.somarov.auth.presentation.http.healthcheck

@Suppress("unused") // Referenced in application.yaml
@ExperimentalSerializationApi
internal fun Application.config() {
    val props = AppProps.parse(environment)
    val cbor = Cbor { ignoreUnknownKeys = true }
    val registry = ObservabilityRegistryFactory.create(props.monitoring)
    val factory = ConnectionFactoryFactory.createFactory(props.db, registry, props.monitoring.name)

    this.monitor.subscribe(ApplicationStopped) {
        factory.dispose()
    }

    val dbClient = DatabaseClient(factory)
    val clientRepo = ClientRepo(dbClient)
    val registrationBroadcastProducer = Producer(
        Producer.ProducerProps(
            props.kafka.brokers,
            props.kafka.producers.registration.maxInFlight,
            props.kafka.producers.registration.topic
        ),
        registry.observationRegistry,
        RegistrationBroadcast::class
    )

    val service = Service(clientRepo, JwtService(props.auth), registrationBroadcastProducer)
    setupScheduler(factory, registry.observationRegistry, this)

    install(ContentNegotiation) { cbor(cbor) }
    install(RequestValidation) { setupValidation(this) }
    install(StatusPages) { setupExceptionHandling(this) }
    install(MicrometerMetrics) { setupMetrics(this, registry.meterRegistry) }
    install(Authentication)
    install(WebSockets)


    // TODO: uncomment when new version of rsocket kotlin will be released
    // install(RSocketSupport) { server { setupRSocketServer(this, registry) } }

    routing {
        healthcheck()
        // TODO: uncomment when new version of rsocket kotlin will be released
        // authSocket(service)
    }
}
