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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.provider.r2dbc.R2dbcLockProvider

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
        executor.executeWithLock(Runnable {
            runBlocking(Dispatchers.IO) {
                val obs = Observation.start(
                    config.name,
                    registry
                )
                val scope = obs.openScope()
                try {
                    withContext(currentCoroutineContext() + registry.asContextElement()) {
                        logger.info("Started task ${config.name}")
                        task()
                        logger.info("Task ${config.name} is completed")
                    }
                } catch (e: Exception) {
                    logger.error("Got error !!!")
                    obs.error(e)
                } finally {
                    scope.close()
                    obs.stop()
                }
            }
        }, config)
    }

    fun stop() {
        runBlocking {
            // check if it allows us to wait until all coroutines are done
            scope.coroutineContext[Job]?.cancelAndJoin()
        }
    }
}
