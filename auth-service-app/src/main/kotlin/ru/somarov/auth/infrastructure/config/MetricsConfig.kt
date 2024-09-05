package ru.somarov.auth.infrastructure.config

import io.ktor.server.metrics.micrometer.*
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics

fun setupMetrics(config: MicrometerMetricsConfig, meterRegistry: MeterRegistry) {
    config.registry = meterRegistry
    config.meterBinders = listOf(JvmMemoryMetrics(), JvmGcMetrics(), ProcessorMetrics())
}
