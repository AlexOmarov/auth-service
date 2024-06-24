package ru.somarov.auth.infrastructure.config.otel

import io.ktor.server.application.ApplicationEnvironment
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

fun createOpenTelemetrySdk(env: ApplicationEnvironment): OpenTelemetrySdk {
    val props = getOpenTelemetryProps(env)

    return OpenTelemetrySdk.builder()
        .setPropagators { W3CTraceContextPropagator.getInstance() }
        .setMeterProvider(buildMeterProvider(props))
        .setLoggerProvider(buildLoggerProvider(props))
        .setTracerProvider(buildTracerProvider(props))
        .build()
}

private fun buildMeterProvider(props: ObservabilityProps): SdkMeterProvider {
    println(props)
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

private fun getOpenTelemetryProps(env: ApplicationEnvironment) = ObservabilityProps(
    name = env.config.property("application.name").getString(),
    protocol = env.config.property("application.otel.protocol").getString(),
    host = env.config.property("application.otel.host").getString(),
    logsPort = env.config.property("application.otel.logsPort").getString().toInt(),
    metricsPort = env.config.property("application.otel.metricsPort").getString().toInt(),
    tracingPort = env.config.property("application.otel.tracingPort").getString().toInt(),
    tracingProbability = env.config.property("application.otel.tracingProbability").getString().toDouble()
)

private data class ObservabilityProps(
    val name: String,
    val protocol: String,
    val host: String,
    val logsPort: Int,
    val metricsPort: Int,
    val tracingPort: Int,
    val tracingProbability: Double
)
