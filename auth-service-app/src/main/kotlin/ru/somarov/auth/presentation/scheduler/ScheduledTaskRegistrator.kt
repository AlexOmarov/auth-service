package ru.somarov.auth.presentation.scheduler

import io.ktor.util.logging.KtorSimpleLogger
import net.javacrumbs.shedlock.core.LockConfiguration
import ru.somarov.auth.infrastructure.scheduler.Scheduler
import java.time.Duration
import java.time.Instant

fun registerTasks(scheduler: Scheduler) {
    val config = LockConfiguration(
        Instant.now(),
        "TEST_TASK",
        Duration.parse("PT1M"),
        Duration.parse("PT1S"),
    )

    scheduler.register(config) {
        KtorSimpleLogger("TEST").info("wow")
    }
}
