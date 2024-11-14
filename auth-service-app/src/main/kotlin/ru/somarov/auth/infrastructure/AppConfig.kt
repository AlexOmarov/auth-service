package ru.somarov.auth.infrastructure

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.cbor.*
import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.requestvalidation.ValidationResult.Invalid
import io.ktor.server.plugins.requestvalidation.ValidationResult.Valid
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.observation.ObservationRegistry
import io.r2dbc.spi.ConnectionFactory
import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.core.RSocketServerBuilder
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import ru.somarov.auth.application.service.AuthenticationService
import ru.somarov.auth.infrastructure.db.repo.ClientRepo
import ru.somarov.auth.lib.db.ConnectionFactoryFactory
import ru.somarov.auth.lib.db.DatabaseClient
import ru.somarov.auth.lib.oid.JwtService
import ru.somarov.auth.lib.kafka.Producer
import ru.somarov.auth.lib.keydb.KeyDbClient
import ru.somarov.auth.lib.observability.ObservabilityRegistry
import ru.somarov.auth.lib.observability.ObservabilityRegistryFactory
import ru.somarov.auth.lib.rsocket.server.Decorator
import ru.somarov.auth.lib.scheduler.Scheduler
import ru.somarov.auth.presentation.event.broadcast.AuthorizationCodeIssuingBroadcast
import ru.somarov.auth.presentation.event.broadcast.RegistrationBroadcast
import ru.somarov.auth.presentation.http.healthcheck
import ru.somarov.auth.presentation.request.ValidationRequest
import ru.somarov.auth.presentation.response.ErrorResponse
import ru.somarov.auth.presentation.scheduler.registerTasks

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

    val keyDbClient = KeyDbClient(props.cache)

    val registrationBroadcastProducer = Producer(
        Producer.ProducerProps(
            props.kafka.brokers,
            props.kafka.producers.registration.maxInFlight,
            props.kafka.producers.registration.topic
        ),
        registry.observationRegistry,
        RegistrationBroadcast::class
    )

    val authBroadcastProducer = Producer(
        Producer.ProducerProps(
            props.kafka.brokers,
            props.kafka.producers.auth.maxInFlight,
            props.kafka.producers.auth.topic
        ),
        registry.observationRegistry,
        AuthorizationCodeIssuingBroadcast::class
    )

    val authenticationService = AuthenticationService(JwtService(props.auth), keyDbClient)
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

fun setupValidation(config: RequestValidationConfig) {
    config.validate<ValidationRequest> {
        if (it.token.isEmpty()) Invalid("A token must not be empty") else Valid
    }
}

fun setupScheduler(factory: ConnectionFactory, registry: ObservationRegistry, app: Application) {
    val scheduler = Scheduler(factory, registry)

    app.monitor.subscribe(ServerReady) {
        registerTasks(scheduler)
        scheduler.start()
    }

    app.monitor.subscribe(ApplicationStopped) {
        scheduler.stop()
    }
}

@OptIn(ExperimentalMetadataApi::class)
fun setupRSocketServer(
    builder: RSocketServerBuilder,
    registry: ObservabilityRegistry
) {
    builder.interceptors {
        forResponder { Decorator.decorate(it, registry) }
    }
}

fun setupMetrics(config: MicrometerMetricsConfig, meterRegistry: MeterRegistry) {
    config.registry = meterRegistry
    config.meterBinders = listOf(JvmMemoryMetrics(), JvmGcMetrics(), ProcessorMetrics())
}

fun setupExceptionHandling(config: StatusPagesConfig) {
    config.exception<Throwable> { call, cause ->
        if (cause is RequestValidationException) {
            call.respond(HttpStatusCode.BadRequest, cause.reasons.joinToString())
        } else {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(mapOf("cause" to (cause.message ?: "undefined")))
            )
        }
    }
}
