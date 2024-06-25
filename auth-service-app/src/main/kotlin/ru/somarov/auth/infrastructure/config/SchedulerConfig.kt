package ru.somarov.auth.infrastructure.config

import io.r2dbc.spi.ConnectionFactory
import net.javacrumbs.shedlock.core.LockConfiguration
import ru.somarov.auth.application.service.SchedulerService
import ru.somarov.auth.infrastructure.scheduler.Scheduler
import java.time.Duration
import java.time.Instant

fun setupScheduler(
    factory: ConnectionFactory,
    service: SchedulerService,
): Scheduler {
    val scheduler = Scheduler(factory)

    scheduler.schedule(
        LockConfiguration(
            Instant.now(),
            "FIRST_TASK",
            Duration.parse("PT1S"),
            Duration.ZERO,
        )
    ) { service.makeWorkInScheduler() }
    scheduler.schedule(
        LockConfiguration(
            Instant.now(),
            "SECOND_TASK",
            Duration.parse("PT2S"),
            Duration.ZERO,
        )
    ) { println("SECOND_TASK") }
    scheduler.schedule(
        LockConfiguration(
            Instant.now(),
            "SECOND_TASK",
            Duration.parse("PT2S"),
            Duration.ZERO,
        )
    ) { println("SECOND_TASK") }
    return scheduler
}