package ru.somarov.auth.presentation.scheduler

import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.core.readBytes
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.javacrumbs.shedlock.core.LockConfiguration
import ru.somarov.auth.infrastructure.scheduler.Scheduler
import ru.somarov.auth.presentation.request.AuthorizationRequest
import java.time.Duration
import java.time.Instant
import java.util.UUID

fun registerTasks(scheduler: Scheduler, client: RSocket) {
    val config = LockConfiguration(
        Instant.now(),
        "TEST_TASK",
        Duration.parse("PT1M"),
        Duration.parse("PT1S"),
    )

    scheduler.register(config) {
        val request = buildPayload { data(Json.Default.encodeToString(AuthorizationRequest(UUID.randomUUID()))) }
        val response = client.requestResponse(request)
        val result = String(response.data.readBytes())
        KtorSimpleLogger("TEST").info(result)
    }
}
