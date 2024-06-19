package ru.somarov.auth.infrastructure.config

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.internal.OtelVersion
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.samplers.Sampler

object OpentelemetrySdkConfig {
    fun sdk(props: ObservabilityProps): OpenTelemetrySdk {
        return OpenTelemetrySdk.builder()
            .setPropagators { W3CTraceContextPropagator.getInstance() }
            .setMeterProvider(buildMeterProvider(props))
            .setLoggerProvider(buildLoggerProvider(props))
            .setTracerProvider(buildTracerProvider(props))
            .build()
    }

    private fun buildMeterProvider(props: ObservabilityProps): SdkMeterProvider {
        val builder = SdkMeterProvider.builder()
        return builder.build()
    }

    private fun buildLoggerProvider(props: ObservabilityProps): SdkLoggerProvider {
        val builder = SdkLoggerProvider
            .builder()
            .addLogRecordProcessor(
                BatchLogRecordProcessor.builder(
                    OtlpGrpcLogRecordExporter.builder()
                        .setEndpoint("${props.protocol}://${props.host}:${props.logsPort}")
                        .build()
                ).build()
            )
            .setResource(
                Resource.create(
                    Attributes.builder()
                        .put("telemetry.sdk.name", "opentelemetry")
                        .put("telemetry.sdk.language", "java")
                        .put("telemetry.sdk.version", OtelVersion.VERSION)
                        .put("service.name", props.name)
                        .build()
                )
            )
        return builder.build()
    }

    private fun buildTracerProvider(props: ObservabilityProps): SdkTracerProvider {
        val sampler = Sampler.parentBased(Sampler.traceIdRatioBased(props.tracingProbability))
        val resource = Resource.getDefault()
            .merge(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), props.name)))

        val builder = SdkTracerProvider.builder().setSampler(sampler).setResource(resource)
            .addSpanProcessor(
                BatchSpanProcessor.builder(
                    OtlpGrpcSpanExporter.builder()
                        .setEndpoint("${props.protocol}://${props.host}:${props.tracingPort}")
                        .build()
                ).build()
            )
        return builder.build()
    }

    data class ObservabilityProps(
        val name: String,
        val protocol: String,
        val host: String,
        val logsPort: Int,
        val metricsPort: Int,
        val tracingPort: Int,
        val tracingProbability: Double
    )
}
