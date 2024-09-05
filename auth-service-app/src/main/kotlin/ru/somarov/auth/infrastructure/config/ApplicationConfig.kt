package ru.somarov.auth.infrastructure.config

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.cbor.cbor
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ServerReady
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.rsocket.kotlin.ktor.server.RSocketSupport
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import ru.somarov.auth.application.service.Service
import ru.somarov.auth.infrastructure.db.DatabaseClient
import ru.somarov.auth.infrastructure.db.repo.ClientRepo
import ru.somarov.auth.infrastructure.db.repo.RevokedAuthorizationRepo
import ru.somarov.auth.infrastructure.observability.setupObservability
import ru.somarov.auth.infrastructure.props.AppProps
import ru.somarov.auth.infrastructure.rsocket.ServerInterceptor
import ru.somarov.auth.infrastructure.scheduler.Scheduler
import ru.somarov.auth.presentation.http.healthcheck
import ru.somarov.auth.presentation.request.ValidationRequest
import ru.somarov.auth.presentation.response.ErrorResponse
import ru.somarov.auth.presentation.rsocket.authSocket
import ru.somarov.auth.presentation.scheduler.registerTasks
import java.util.TimeZone

@Suppress("unused") // Referenced in application.yaml
@OptIn(ExperimentalSerializationApi::class)
internal fun Application.config() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    val props = AppProps.parseProps(environment)

    val (meterRegistry, observationRegistry) = setupObservability(props)

    val dbClient = DatabaseClient(props, meterRegistry)

    val clientRepo = ClientRepo(dbClient)
    val revokedAuthorizationRepo = RevokedAuthorizationRepo(dbClient)

    val service = Service(clientRepo, revokedAuthorizationRepo)

    val scheduler = Scheduler(dbClient.factory, observationRegistry)

    install(ContentNegotiation) { cbor(Cbor { ignoreUnknownKeys = true }) }

    install(MicrometerMetrics) {
        registry = meterRegistry
        meterBinders = listOf(JvmMemoryMetrics(), JvmGcMetrics(), ProcessorMetrics())
    }

    install(RequestValidation) {
        validate<ValidationRequest> { request ->
            if (request.token.isEmpty())
                ValidationResult.Invalid("A token must not be empty")
            else
                ValidationResult.Valid
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
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

    install(WebSockets)

    install(RSocketSupport) {
        server {
            interceptors {
                forResponder(ServerInterceptor(meterRegistry, observationRegistry))
            }
        }
    }

    environment.monitor.subscribe(ServerReady) {
        registerTasks(scheduler)
        scheduler.start()
    }

    environment.monitor.subscribe(ApplicationStopped) {
        scheduler.stop()
    }

    routing {
        healthcheck()
        authSocket(service)
    }
}
