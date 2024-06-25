package ru.somarov.auth.infrastructure.config

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
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
import io.rsocket.micrometer.observation.ByteBufGetter
import io.rsocket.micrometer.observation.ByteBufSetter
import io.rsocket.micrometer.observation.RSocketRequesterTracingObservationHandler
import io.rsocket.micrometer.observation.RSocketResponderTracingObservationHandler
import ru.somarov.auth.infrastructure.otel.context.ApplicationCallReceiverContext
import ru.somarov.auth.infrastructure.otel.context.ApplicationCallSenderContext
import ru.somarov.auth.infrastructure.otel.createOpenTelemetrySdk
import java.util.Properties

fun setupObservability(application: Application): Pair<MeterRegistry, ObservationRegistry> {
    val environment = application.environment
    val sdk = createOpenTelemetrySdk(environment)
    val buildProps = getBuildProperties()
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

    Gauge.builder("project_version") { 1 }
        .description("Version of project in tag")
        .tag("version", buildProps.getProperty("version", "undefined"))
        .register(meterRegistry)

    application.install(MicrometerMetrics) {
        registry = meterRegistry
        meterBinders = listOf(JvmMemoryMetrics(), JvmGcMetrics(), ProcessorMetrics())
    }

    return Pair(meterRegistry, observationRegistry)
}

private fun getBuildProperties(): Properties {
    val properties = Properties()
    Application::class.java.getResourceAsStream("/META-INF/build-info.properties")?.use {
        properties.load(it)
    }
    return properties
}