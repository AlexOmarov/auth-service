package ru.somarov.auth.infrastructure

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ServerReady
import io.ktor.server.metrics.micrometer.MicrometerMetricsConfig
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.requestvalidation.ValidationResult.*
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.response.respond
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.observation.ObservationRegistry
import io.r2dbc.spi.ConnectionFactory
import ru.somarov.auth.infrastructure.lib.scheduler.Scheduler
import ru.somarov.auth.presentation.request.ValidationRequest
import ru.somarov.auth.presentation.response.ErrorResponse
import ru.somarov.auth.presentation.scheduler.registerTasks

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
