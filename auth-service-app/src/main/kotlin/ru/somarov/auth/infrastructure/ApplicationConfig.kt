package ru.somarov.auth.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.server.application.Application
import io.ktor.server.application.ServerReady
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.util.logging.KtorSimpleLogger
import io.micrometer.core.instrument.kotlin.asContextElement
import io.micrometer.observation.Observation
import io.rsocket.kotlin.ktor.client.RSocketSupport
import io.rsocket.kotlin.ktor.client.rSocket
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.TimeZone

@Suppress("unused") // Referenced in application.yaml
internal fun Application.config() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    install(io.ktor.server.websocket.WebSockets)
    install(io.rsocket.kotlin.ktor.server.RSocketSupport)

    val (_, observationRegistry) = setupObservability(this)

    routing {
        authSocket()
    }

    environment.monitor.subscribe(ServerReady) {
        val logger = KtorSimpleLogger("TEST")

        launch {
            while (true) {
                val client = HttpClient(CIO) {
                    install(WebSockets)
                    install(RSocketSupport)
                }
                val rcks = client.rSocket(path = "login", port = 9099)
                try {
                    Observation.createNotStarted("TEST", observationRegistry).observeAndAwait {
                        logger.info("Started task TEST")
                        rcks.requestResponse(buildPayload { data("WOW") })
                        logger.info("Task TEST is completed")
                    }
                } catch (e: Exception) {
                    logger.error("Got ex ${e.message}", e)
                }
                client.close()
                println("ITS OVER")
                delay(1000)
            }
        }
    }
}

@Suppress("TooGenericExceptionCaught")
suspend fun <T> Observation.observeAndAwait(func: suspend () -> T) {
    start()
    try {
        withContext(openScope().use { observationRegistry.asContextElement() }) {
            func()
        }
    } catch (error: Throwable) {
        error(error)
        throw error
    } finally {
        stop()
    }
}
