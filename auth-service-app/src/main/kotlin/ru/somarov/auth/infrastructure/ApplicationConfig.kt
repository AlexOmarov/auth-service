package ru.somarov.auth.infrastructure

import createOpenTelemetrySdk
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.cbor.cbor
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.ApplicationEnvironment
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
import ru.somarov.auth.application.Service
import ru.somarov.auth.infrastructure.config.otel.context.ApplicationCallReceiverContext
import ru.somarov.auth.infrastructure.config.otel.context.ApplicationCallSenderContext
import ru.somarov.auth.infrastructure.rsocket.ServerObservabilityInterceptor
import ru.somarov.auth.presentation.auth
import ru.somarov.auth.presentation.authSocket
import ru.somarov.auth.presentation.request.AuthorizationRequest
import ru.somarov.auth.presentation.response.ErrorResponse
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
            "ktor_${environment.get("application.name")}",
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
            "application", environment.get("application.name"),
            "instance", environment.get("application.instance")
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

    val service = Service(environment)

    routing {
        openAPI("openapi")
        swaggerUI("swagger")

        auth(service)
        authSocket(service)
    }
}

private fun getBuildProperties(): Properties {
    val properties = Properties()
    Application::class.java.getResourceAsStream("/META-INF/build-info.properties")?.use {
        properties.load(it)
    }
    return properties
}

private fun ApplicationEnvironment.get(path: String): String {
    return this.config.property(path).getString()
}
