package ru.somarov.auth.infrastructure

import io.ktor.serialization.kotlinx.cbor.cbor
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.ApplicationEnvironment
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.util.logging.KtorSimpleLogger
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.transport.ReceiverContext
import io.micrometer.registry.otlp.OtlpConfig
import io.micrometer.registry.otlp.OtlpMeterRegistry
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext
import io.micrometer.tracing.otel.bridge.OtelPropagator
import io.micrometer.tracing.otel.bridge.OtelTracer
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.rsocket.kotlin.ktor.server.RSocketSupport
import io.rsocket.micrometer.observation.ByteBufGetter
import io.rsocket.micrometer.observation.ByteBufSetter
import io.rsocket.micrometer.observation.RSocketRequesterTracingObservationHandler
import io.rsocket.micrometer.observation.RSocketResponderTracingObservationHandler
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import ru.somarov.auth.application.Service
import ru.somarov.auth.infrastructure.rsocket.ServerObservabilityInterceptor
import ru.somarov.auth.presentation.auth
import ru.somarov.auth.presentation.authSocket

@Suppress("unused") // Referenced in application.yaml
@OptIn(ExperimentalSerializationApi::class)
internal fun Application.config() {
    val logger = KtorSimpleLogger(this.javaClass.name)
    val env = environment
    val oteltracer = GlobalOpenTelemetry.getTracer("global")
    val tracer = OtelTracer(oteltracer, OtelCurrentTraceContext()) { }
    val propagator = OtelPropagator(ContextPropagators.create(W3CTraceContextPropagator.getInstance()), oteltracer)
    val observationRegistry = ObservationRegistry.create().also {
        it.observationConfig()
            .observationHandler(
                RSocketRequesterTracingObservationHandler(tracer, propagator, ByteBufSetter(), false)
            )
            .observationHandler(
                RSocketResponderTracingObservationHandler(tracer, propagator, ByteBufGetter(), false)
            )
    }

    val meterRegistry = OtlpMeterRegistry(OtlpConfig.DEFAULT, Clock.SYSTEM).also {
        it.config().commonTags(
            "application", env.get("application.name"),
            "instance", env.get("application.instance")
        )
    }

    install(MicrometerMetrics) {
        registry = meterRegistry
        meterBinders = listOf(
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics()
        )
    }

    install(ContentNegotiation) { cbor(Cbor { ignoreUnknownKeys = true }) }

    install(WebSockets)
    install(RSocketSupport) {
        server {
            interceptors {
                forResponder(ServerObservabilityInterceptor(meterRegistry, observationRegistry))
            }
        }
    }

    intercept(ApplicationCallPipeline.Monitoring) {
        val observation = Observation.start(
            "http_${call.request.path()}",
            { ReceiverContext<ApplicationCall> { carrier, key -> carrier.request.headers[key] }.also { it.carrier = call } },
            observationRegistry
        )
        try {
            observation.openScope().use {
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

        } catch (error: Throwable) {
            logger.error("Got exception while trying to observe http request: ${error.message}", error)
            throw error
        } finally {
            observation.stop()
            logger.info("Closed observation $observation")
        }
    }

    val service = Service(env)

    routing {
        openAPI("openapi")
        swaggerUI("swagger")

        auth(service)
        authSocket(Service(env))
    }
}

private fun ApplicationEnvironment.get(path: String): String {
    return this.config.property(path).getString()
}
