package ru.somarov.auth.infrastructure

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import io.micrometer.registry.otlp.OtlpConfig
import io.micrometer.registry.otlp.OtlpMeterRegistry
import io.micrometer.tracing.handler.DefaultTracingObservationHandler
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler
import io.micrometer.tracing.otel.bridge.EventPublishingContextWrapper
import io.micrometer.tracing.otel.bridge.OtelBaggageManager
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext
import io.micrometer.tracing.otel.bridge.OtelPropagator
import io.micrometer.tracing.otel.bridge.OtelTracer
import io.micrometer.tracing.otel.bridge.Slf4JBaggageEventListener
import io.micrometer.tracing.otel.bridge.Slf4JEventListener
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.ContextStorage
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.internal.OtelVersion
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.rsocket.micrometer.observation.ByteBufGetter
import io.rsocket.micrometer.observation.ByteBufSetter
import io.rsocket.micrometer.observation.RSocketRequesterTracingObservationHandler
import io.rsocket.micrometer.observation.RSocketResponderTracingObservationHandler
import java.util.Collections

fun setupObservability(application: Application): Pair<MeterRegistry, ObservationRegistry> {
    val sdk = createOpenTelemetrySdk()

    val meterRegistry = OtlpMeterRegistry(OtlpConfig.DEFAULT, Clock.SYSTEM).also {
        it.config().commonTags(
            "application", "auth",
            "instance", "auth"
        )
    }

    Gauge.builder("project_version") { 1 }
        .description("Version of project in tag")
        .tag("version", "12")
        .register(meterRegistry)

    application.install(MicrometerMetrics) {
        registry = meterRegistry
        meterBinders = listOf(JvmMemoryMetrics(), JvmGcMetrics(), ProcessorMetrics())
    }

    val oteltracer = sdk.tracerProvider["io.micrometer.micrometer-tracing"]
    val context = OtelCurrentTraceContext()
    val listener = Slf4JEventListener()
    val baggageListener = Slf4JBaggageEventListener(Collections.emptyList())
    val publisher = { it: Any -> listener.onEvent(it); baggageListener.onEvent(it) }
    val tracer = OtelTracer(oteltracer, context, publisher)
    val propagator = OtelPropagator(sdk.propagators, oteltracer)

    val observationRegistry = ObservationRegistry.create().also {
        it.observationConfig()
            .observationHandler(DefaultMeterObservationHandler(meterRegistry))
            .observationHandler(
                ObservationHandler.FirstMatchingCompositeObservationHandler(
                    RSocketRequesterTracingObservationHandler(tracer, propagator, ByteBufSetter(), false),
                    RSocketResponderTracingObservationHandler(tracer, propagator, ByteBufGetter(), false),
                    PropagatingReceiverTracingObservationHandler(tracer, propagator),
                    PropagatingSenderTracingObservationHandler(tracer, propagator),
                    DefaultTracingObservationHandler(tracer)
                )
            )
    }

    ContextStorage.addWrapper(EventPublishingContextWrapper(publisher))
    // OpenTelemetryAppender.install(sdk)

    return Pair(meterRegistry, observationRegistry)
}

private fun createOpenTelemetrySdk(): OpenTelemetrySdk {
    return OpenTelemetrySdk.builder()
        .setPropagators { W3CTraceContextPropagator.getInstance() }
        .setMeterProvider(buildMeterProvider())
        .setLoggerProvider(buildLoggerProvider())
        .setTracerProvider(buildTracerProvider())
        .build()
}

private fun buildMeterProvider(): SdkMeterProvider {
    val builder = SdkMeterProvider.builder()
    return builder.build()
}

private fun buildLoggerProvider(): SdkLoggerProvider {
    val builder = SdkLoggerProvider
        .builder()
        .addLogRecordProcessor(
            BatchLogRecordProcessor.builder(
                OtlpGrpcLogRecordExporter.builder()
                    .setEndpoint("http://localhost:4317")
                    .build()
            ).build()
        )
        .setResource(
            Resource.create(
                Attributes.builder()
                    .put("telemetry.sdk.name", "opentelemetry")
                    .put("telemetry.sdk.language", "java")
                    .put("telemetry.sdk.version", OtelVersion.VERSION)
                    .put("service.name", "auth")
                    .build()
            )
        )
    return builder.build()
}

private fun buildTracerProvider(): SdkTracerProvider {
    val sampler = Sampler.parentBased(Sampler.traceIdRatioBased(1.0))
    val resource = Resource.getDefault()
        .merge(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "auth")))

    val builder = SdkTracerProvider.builder().setSampler(sampler).setResource(resource)
        .addSpanProcessor(
            BatchSpanProcessor.builder(
                OtlpGrpcSpanExporter.builder()
                    .setEndpoint("http://localhost:4319")
                    .build()
            ).build()
        )
    return builder.build()
}
