package ru.somarov.auth.infrastructure

import io.ktor.serialization.kotlinx.cbor.cbor
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationEnvironment
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
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
import ru.somarov.auth.infrastructure.rsocket.ServerLoggingInterceptor
import ru.somarov.auth.infrastructure.rsocket.ServerMetricsInterceptor
import ru.somarov.auth.infrastructure.rsocket.ServerTracingInterceptor
import ru.somarov.auth.presentation.auth
import ru.somarov.auth.presentation.authSocket

@Suppress("unused") // Referenced in application.yaml
@OptIn(ExperimentalSerializationApi::class)
internal fun Application.config() {
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

    install(CallId) {
        retrieve { call ->
            val dd = Observation.createNotStarted(
                "http_call.request.path()",
                { ReceiverContext<ApplicationCall> { carrier, key -> "" } },
                observationRegistry)
            ""
        }
    }
    install(CallLogging)

    install(ContentNegotiation) { cbor(Cbor { ignoreUnknownKeys = true }) }

    install(WebSockets)
    install(RSocketSupport) {
        server {
            interceptors {
                forResponder(ServerLoggingInterceptor())
                forResponder(ServerTracingInterceptor(observationRegistry))
                forResponder(ServerMetricsInterceptor(meterRegistry))
            }
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
