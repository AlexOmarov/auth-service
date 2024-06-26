package ru.somarov.auth.presentation.scheduler

import net.javacrumbs.shedlock.core.LockConfiguration
import ru.somarov.auth.application.service.SchedulerService
import ru.somarov.auth.infrastructure.scheduler.Scheduler
import java.time.Duration
import java.time.Instant

fun registerTasks(scheduler: Scheduler, service: SchedulerService) {
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
    ) { println("SECOND_TASK") }
    scheduler.schedule(
        LockConfiguration(
            Instant.now(),
            "SECOND_TASK",
            Duration.parse("PT1M"),
            Duration.parse("PT1S"),
        )
    ) { println("SECOND_TASK") }
}
