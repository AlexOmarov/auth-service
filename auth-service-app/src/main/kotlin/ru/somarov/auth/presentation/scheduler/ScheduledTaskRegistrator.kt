package ru.somarov.auth.presentation.scheduler

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.rsocket.kotlin.core.WellKnownMimeType
import io.rsocket.kotlin.ktor.client.RSocketSupport
import io.rsocket.kotlin.ktor.client.rSocket
import io.rsocket.kotlin.payload.PayloadMimeType
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import net.javacrumbs.shedlock.core.LockConfiguration
import ru.somarov.auth.application.service.SchedulerService
import ru.somarov.auth.infrastructure.scheduler.Scheduler
import java.time.Duration
import java.time.Instant

fun registerTasks(scheduler: Scheduler, service: SchedulerService) {
    val ktorClient = HttpClient(CIO) {
        install(WebSockets)
        install(RSocketSupport) {
            connector {
                connectionConfig {
                    payloadMimeType = PayloadMimeType(
                        data = WellKnownMimeType.ApplicationJson,
                        metadata = WellKnownMimeType.MessageRSocketCompositeMetadata
                    )
                }
            }
        }
    }

    scheduler.schedule(
        LockConfiguration(
            Instant.now(),
            "FIRST_TASK",
            Duration.parse("PT1M"),
            Duration.parse("PT1S"),
        )
    ) { service.makeWorkInScheduler() }
    scheduler.schedule(
        LockConfiguration(
            Instant.now(),
            "SECOND_TASK",
            Duration.parse("PT1M"),
            Duration.parse("PT1S"),
        )
    ) {
        ktorClient.rSocket(path = "login", port = 9099).requestResponse(
            buildPayload {
                data("""{ "data": "hello world" }""")
            }
        )
    }
}
