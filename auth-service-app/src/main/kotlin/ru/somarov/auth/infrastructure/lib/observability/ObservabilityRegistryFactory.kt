package ru.somarov.auth.infrastructure.lib.observability

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import io.micrometer.tracing.handler.DefaultTracingObservationHandler
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler
import io.micrometer.tracing.handler.TracingAwareMeterObservationHandler
import io.micrometer.tracing.otel.bridge.EventPublishingContextWrapper
import io.micrometer.tracing.otel.bridge.OtelBaggageManager
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext
import io.micrometer.tracing.otel.bridge.OtelPropagator
import io.micrometer.tracing.otel.bridge.OtelTracer
import io.micrometer.tracing.otel.bridge.Slf4JBaggageEventListener
import io.micrometer.tracing.otel.bridge.Slf4JEventListener
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.ContextStorage
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry
import io.opentelemetry.sdk.OpenTelemetrySdk
import ru.somarov.auth.infrastructure.lib.observability.opentelemetry.OpenTelemetrySdkFactory
import ru.somarov.auth.infrastructure.lib.observability.props.OtelProps

object ObservabilityRegistryFactory {
    fun create(props: OtelProps): ObservabilityRegistry {
        val sdk = OpenTelemetrySdkFactory.create(props)

        val meterRegistry = createMeterRegistry(sdk, props)
        val observationRegistry = createObservationRegistry(sdk, meterRegistry)

        OpenTelemetryAppender.install(sdk)

        return ObservabilityRegistry(meterRegistry, observationRegistry)
    }

    private fun createObservationRegistry(sdk: OpenTelemetrySdk, meterRegistry: MeterRegistry): ObservationRegistry {
        val tracer = sdk.tracerProvider["io.micrometer.micrometer-tracing"]

        val propagator = OtelPropagator(sdk.propagators, tracer)
        val tracerWrapper = wrapTracer(tracer)

        val registry = ObservationRegistry.create()

        val propagationHandler = ObservationHandler.FirstMatchingCompositeObservationHandler(
            PropagatingReceiverTracingObservationHandler(tracerWrapper, propagator),
            PropagatingSenderTracingObservationHandler(tracerWrapper, propagator),
            DefaultTracingObservationHandler(tracerWrapper)
        )

        val meterHandler = TracingAwareMeterObservationHandler(
            DefaultMeterObservationHandler(meterRegistry),
            tracerWrapper
        )

        registry.observationConfig()
            .observationHandler(propagationHandler)
            .observationHandler(meterHandler)

        return registry
    }

    private fun wrapTracer(tracer: Tracer): OtelTracer {
        val context = OtelCurrentTraceContext()
        val listener = Slf4JEventListener()
        val baggageListener = Slf4JBaggageEventListener(mutableListOf<String>())
        val publisher = { it: Any -> listener.onEvent(it); baggageListener.onEvent(it) }
        ContextStorage.addWrapper(EventPublishingContextWrapper(publisher))

        val baggage = OtelBaggageManager(context, mutableListOf<String>(), mutableListOf<String>())

        return OtelTracer(tracer, context, publisher, baggage)
    }

    private fun createMeterRegistry(sdk: OpenTelemetrySdk, props: OtelProps): MeterRegistry {
        val registry = OpenTelemetryMeterRegistry.create(sdk)

        registry
            .config()
            .commonTags(
                "application", props.name,
                "instance", props.instance
            )

        Gauge.builder("project_version") { 1 }
            .tag("version", props.build.version)
            .description("Version of project in tag")
            .register(registry)

        return registry
    }
}
