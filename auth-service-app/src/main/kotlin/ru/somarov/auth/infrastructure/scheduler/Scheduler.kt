package ru.somarov.auth.infrastructure.scheduler

import io.ktor.util.logging.KtorSimpleLogger
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.javacrumbs.shedlock.core.LockConfiguration
import ru.somarov.auth.infrastructure.micrometer.observeSuspended
import java.util.concurrent.CancellationException

class Scheduler(private val registry: ObservationRegistry) {
    private val logger = KtorSimpleLogger(this.javaClass.name)

    private val tasks = mutableListOf<Pair<suspend () -> Unit, LockConfiguration>>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun register(config: LockConfiguration, task: suspend () -> Unit) {
        tasks.add(task to config)
    }

    fun start() {
        tasks.forEach { (task, config) -> schedule(task, config) }
    }

    fun stop() {
        runBlocking { scope.cancel(CancellationException("Got cancellation of scheduler")) }
    }

    private fun schedule(task: suspend () -> Unit, config: LockConfiguration) {
        scope.launch {
            while (isActive) {
                val startTime = System.currentTimeMillis()
                execute(task, config)
                val endTime = System.currentTimeMillis()
                delay(maxOf(0, config.lockAtLeastFor.toMillis() - endTime + startTime))
            }
        }
    }

    private suspend fun execute(task: suspend () -> Unit, config: LockConfiguration) {
        Observation.createNotStarted(config.name, registry).observeSuspended {
            logger.info("Started task ${config.name}")
            task()
            logger.info("Task ${config.name} is completed")
        }
    }
}
