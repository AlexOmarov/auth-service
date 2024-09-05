package ru.somarov.auth.infrastructure.config

import io.ktor.server.application.*
import io.micrometer.observation.ObservationRegistry
import io.r2dbc.spi.ConnectionFactory
import ru.somarov.auth.infrastructure.scheduler.Scheduler
import ru.somarov.auth.presentation.scheduler.registerTasks

fun setupScheduler(factory: ConnectionFactory, registry: ObservationRegistry, env: ApplicationEnvironment) {
    val scheduler = Scheduler(factory, registry)

    env.monitor.subscribe(ServerReady) {
        registerTasks(scheduler)
        scheduler.start()
    }

    env.monitor.subscribe(ApplicationStopped) {
        scheduler.stop()
    }
}
