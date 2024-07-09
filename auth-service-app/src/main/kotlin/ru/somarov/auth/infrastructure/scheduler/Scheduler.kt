package ru.somarov.auth.infrastructure.scheduler

import io.ktor.util.logging.KtorSimpleLogger
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.provider.r2dbc.R2dbcLockProvider
import ru.somarov.auth.infrastructure.observability.micrometer.observeAndAwait
import java.util.concurrent.CancellationException

class Scheduler(factory: ConnectionFactory, private val registry: ObservationRegistry) {
    private val logger = KtorSimpleLogger(this.javaClass.name)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tasks = mutableListOf<Pair<suspend () -> Unit, LockConfiguration>>()
    private val executor = DefaultLockingTaskExecutor(R2dbcLockProvider(factory))

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

                val deferred = CompletableDeferred<Unit>()
                var isExecuting = false
                @Suppress("TooGenericExceptionCaught") // have to catch all exceptions to complete deferred
                executor.executeWithLock(kotlinx.coroutines.Runnable {
                    isExecuting = true
                    runBlocking(Dispatchers.IO) {
                        try {
                            execute(task, config)
                        } catch (e: Throwable) {
                            logger.error("Got error executing")
                        }
                        deferred.complete(Unit)
                    }
                }, config)
                if (isExecuting) {
                    deferred.await()
                }
                val endTime = System.currentTimeMillis()
                delay(maxOf(0, config.lockAtLeastFor.toMillis() - endTime + startTime))
            }
        }
    }

    private suspend fun execute(task: suspend () -> Unit, config: LockConfiguration) {
        Observation.createNotStarted(config.name, registry).observeAndAwait {
            logger.info("Started task ${config.name}")
            task()
            logger.info("Task ${config.name} is completed")
        }
    }
}
