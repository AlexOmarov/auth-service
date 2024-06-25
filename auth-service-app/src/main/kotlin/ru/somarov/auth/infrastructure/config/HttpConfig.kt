package ru.somarov.auth.infrastructure.config

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.util.logging.KtorSimpleLogger
import io.micrometer.core.instrument.kotlin.asContextElement
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import ru.somarov.auth.infrastructure.otel.context.ApplicationCallReceiverContext

fun setupHttp(application: Application, observationRegistry: ObservationRegistry) {
    val logger = KtorSimpleLogger("RequestResponseConfig")

    application.intercept(ApplicationCallPipeline.Monitoring) {
        val kk = ApplicationCallReceiverContext()
        kk.carrier = call
        val observation = Observation.start(
            "http_${call.request.path()}",
            { kk },
            observationRegistry
        )
        @Suppress("TooGenericExceptionCaught") // had to catch all exception to kog
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
}
