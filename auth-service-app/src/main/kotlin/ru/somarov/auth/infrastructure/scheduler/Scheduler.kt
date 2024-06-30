package ru.somarov.auth.infrastructure.scheduler

import io.ktor.util.logging.KtorSimpleLogger
import io.micrometer.core.instrument.kotlin.asContextElement
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.provider.r2dbc.R2dbcLockProvider
import java.time.OffsetDateTime
import java.util.UUID
import java.util.function.Supplier

class Scheduler(factory: ConnectionFactory, private val registry: ObservationRegistry) {
    private val logger = KtorSimpleLogger(this.javaClass.name)
    private val tasks = mutableListOf<Pair<suspend () -> Unit, LockConfiguration>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val executor = DefaultLockingTaskExecutor(R2dbcLockProvider(factory))

    fun schedule(config: LockConfiguration, task: suspend () -> Unit) {
        tasks.add(task to config)
    }

    fun start() {
        tasks.forEach { (task, config) ->
            scope.launch {
                while (isActive) {
                    val startTime = System.currentTimeMillis()
                    schedule(task, config)
                    val endTime = System.currentTimeMillis()
                    delay(maxOf(0, config.lockAtLeastFor.toMillis() - endTime + startTime))
                }
            }
        }
    }

    private suspend fun schedule(task: suspend () -> Unit, config: LockConfiguration) {
        @Suppress("TooGenericExceptionCaught") // have to catch all exceptions to complete deferred
        val obs = Observation.createNotStarted(
            config.name,
            registry
        )
        obs.start()
        obs.openScope().use {
            withContext(currentCoroutineContext() + registry.asContextElement()) {
                logger.info("Started task ${config.name}")
                task()
                logger.info("Task ${config.name} is completed")
            }
        }
        obs.stop()
    }

    fun stop() {
        runBlocking {
            // check if it allows us to wait until all coroutines are done
            scope.coroutineContext[Job]?.cancelAndJoin()
        }
    }
}
