package ru.somarov.auth.infrastructure

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.cbor.cbor
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.util.logging.KtorSimpleLogger
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.kotlin.asContextElement
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.micrometer.registry.otlp.OtlpConfig
import io.micrometer.registry.otlp.OtlpMeterRegistry
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler
import io.micrometer.tracing.otel.bridge.EventPublishingContextWrapper
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext
import io.micrometer.tracing.otel.bridge.OtelPropagator
import io.micrometer.tracing.otel.bridge.OtelTracer
import io.micrometer.tracing.otel.bridge.Slf4JEventListener
import io.opentelemetry.context.ContextStorage
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import io.rsocket.kotlin.ktor.server.RSocketSupport
import io.rsocket.micrometer.observation.ByteBufGetter
import io.rsocket.micrometer.observation.ByteBufSetter
import io.rsocket.micrometer.observation.RSocketRequesterTracingObservationHandler
import io.rsocket.micrometer.observation.RSocketResponderTracingObservationHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.provider.r2dbc.R2dbcLockProvider
import ru.somarov.auth.application.scheduler.Scheduler
import ru.somarov.auth.application.service.Service
import ru.somarov.auth.infrastructure.config.otel.context.ApplicationCallReceiverContext
import ru.somarov.auth.infrastructure.config.otel.context.ApplicationCallSenderContext
import ru.somarov.auth.infrastructure.config.otel.createOpenTelemetrySdk
import ru.somarov.auth.infrastructure.db.DatabaseClient
import ru.somarov.auth.infrastructure.db.repo.ClientRepo
import ru.somarov.auth.infrastructure.rsocket.ServerObservabilityInterceptor
import ru.somarov.auth.presentation.consumers.MailConsumer
import ru.somarov.auth.presentation.consumers.RetryConsumer
import ru.somarov.auth.presentation.http.auth
import ru.somarov.auth.presentation.request.AuthorizationRequest
import ru.somarov.auth.presentation.response.ErrorResponse
import ru.somarov.auth.presentation.rsocket.authSocket
import java.time.Duration
import java.time.Instant
import java.util.Properties
import java.util.TimeZone

@Suppress("unused", "LongMethod", "CyclomaticComplexMethod") // Referenced in application.yaml
@OptIn(ExperimentalSerializationApi::class)
internal fun Application.config() {
    val logger = KtorSimpleLogger(this.javaClass.name)

    val buildProps = getBuildProperties()

    val sdk = createOpenTelemetrySdk(environment)

    val oteltracer =
        sdk.getTracer(
            "ktor_${environment.config.property("application.name").getString()}",
            buildProps.getProperty("build.version", "undefined")
        )
    val listener = Slf4JEventListener()
    val publisher = { it: Any -> listener.onEvent(it) }
    val tracer = OtelTracer(oteltracer, OtelCurrentTraceContext(), publisher)
    val propagator = OtelPropagator(sdk.propagators, oteltracer)
    val observationRegistry = ObservationRegistry.create().also {
        it.observationConfig()
            .observationHandler(
                PropagatingReceiverTracingObservationHandler<ApplicationCallReceiverContext>(tracer, propagator)
            )
            .observationHandler(
                PropagatingSenderTracingObservationHandler<ApplicationCallSenderContext>(tracer, propagator)
            )
            .observationHandler(
                RSocketRequesterTracingObservationHandler(tracer, propagator, ByteBufSetter(), false)
            )
            .observationHandler(
                RSocketResponderTracingObservationHandler(tracer, propagator, ByteBufGetter(), false)
            )
    }

    ContextStorage.addWrapper(EventPublishingContextWrapper(publisher))
    OpenTelemetryAppender.install(sdk)

    val meterRegistry = OtlpMeterRegistry(OtlpConfig.DEFAULT, Clock.SYSTEM).also {
        it.config().commonTags(
            "application", environment.config.property("application.name").getString(),
            "instance", environment.config.property("application.instance").getString()
        )
    }

    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    Gauge.builder("project_version") { 1 }
        .description("Version of project in tag")
        .tag("version", buildProps.getProperty("version", "undefined"))
        .register(meterRegistry)

    install(MicrometerMetrics) {
        registry = meterRegistry
        meterBinders = listOf(JvmMemoryMetrics(), JvmGcMetrics(), ProcessorMetrics())
    }

    install(ContentNegotiation) { cbor(Cbor { ignoreUnknownKeys = true }) }
    install(ContentNegotiation) { json() }

    install(RequestValidation) {
        validate<AuthorizationRequest> { request ->
            if (request.authorization.accessToken.isEmpty())
                ValidationResult.Invalid("A customer ID should be greater than 0")
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
                forResponder(ServerObservabilityInterceptor(meterRegistry, observationRegistry))
            }
        }
    }

    @Suppress("TooGenericExceptionCaught") // had to catch all exception to kog
    intercept(ApplicationCallPipeline.Monitoring) {
        val kk = ApplicationCallReceiverContext()
        kk.carrier = call
        val observation = Observation.start(
            "http_${call.request.path()}",
            { kk },
            observationRegistry
        )
        try {
            observation.openScope().use {
                withContext(currentCoroutineContext() + observationRegistry.asContextElement() + Dispatchers.IO) {
                    logger.info(
                        ">>> HTTP ${call.request.origin.method.value} ${call.request.path()} - " +
                            "headers: ${call.request.headers.entries().map { "${it.key}: ${it.value}" }}, " +
                            "body: ${call.receiveText()}"
                    )
                    proceed()
                    logger.info(
                        "<<< HTTP ${call.request.origin.method.value} ${call.request.path()} - " +
                            "headers: ${call.response.headers.allValues().entries().map { "${it.key}: ${it.value}" }}, "
                    )
                }
            }
        } catch (error: Throwable) {
            logger.error("Got exception while trying to observe http request: ${error.message}", error)
            throw error
        } finally {
            observation.stop()
        }
    }
    val dbClient = DatabaseClient(environment, meterRegistry)
    val repo = ClientRepo(dbClient)
    val service = Service(repo)
    val executor = DefaultLockingTaskExecutor(R2dbcLockProvider(dbClient.factory))
    val scheduler = Scheduler(executor)

    scheduler.schedule(
        LockConfiguration(
            Instant.now(),
            "FIRST_TASK",
            Duration.parse("PT1S"),
            Duration.ZERO,
        )
    ) { println("FIRST_TASK") } // every 5000 milliseconds
    scheduler.schedule(
        LockConfiguration(
            Instant.now(),
            "SECOND_TASK",
            Duration.parse("PT2S"),
            Duration.ZERO,
        )
    ) { println("SECOND_TASK") } // every 10000 milliseconds
    scheduler.schedule(
        LockConfiguration(
            Instant.now(),
            "SECOND_TASK",
            Duration.parse("PT2S"),
            Duration.ZERO,
        )
    ) { println("SECOND_TASK") } // every 10000 milliseconds

    scheduler.start()

    executor.executeWithLock(
        Runnable { println("hello") },
        LockConfiguration(
            Instant.now(),
            "LOCK_NAME",
            Duration.parse("PT10S"),
            Duration.ZERO,
        )
    )

    routing {
        openAPI("openapi")
        swaggerUI("swagger")

        auth(service)
        authSocket(service)
    }

    val consumer = MailConsumer(service, environment, observationRegistry)
    val retryConsumer = RetryConsumer(environment, listOf(consumer), observationRegistry)

    environment.monitor.subscribe(ApplicationStarted) {
        println("My app is ready to roll")
        consumer.start()
        retryConsumer.start()

    }

    environment.monitor.subscribe(ApplicationStopped) {
        scheduler.stop()
    }

}

private fun getBuildProperties(): Properties {
    val properties = Properties()
    Application::class.java.getResourceAsStream("/META-INF/build-info.properties")?.use {
        properties.load(it)
    }
    return properties
}
