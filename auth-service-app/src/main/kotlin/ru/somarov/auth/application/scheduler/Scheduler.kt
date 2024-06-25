package ru.somarov.auth.application.scheduler

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor
import net.javacrumbs.shedlock.core.LockConfiguration

class Scheduler(private val executor: DefaultLockingTaskExecutor) {
    private val tasks = mutableListOf<Pair<suspend () -> Unit, LockConfiguration>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun schedule(config: LockConfiguration, task: suspend () -> Unit) {
        tasks.add(task to config)
    }

    fun start() {
        tasks.forEach { (task, config) ->
            scope.launch {
                while (isActive) {
                    val deferred = CompletableDeferred<Unit>()
                    val startTime = System.currentTimeMillis()
                    @Suppress("TooGenericExceptionCaught") // have to catch all exceptions to complete deferred
                    executor.executeWithLock(Runnable {
                        launch {
                            try {
                                task()
                                deferred.complete(Unit)
                            } catch (e: Exception) {
                                deferred.completeExceptionally(e)
                            }
                        }
                    }, config)
                    deferred.await()
                    val endTime = System.currentTimeMillis()
                    val elapsedTime = endTime - startTime
                    delay(maxOf(0, config.lockAtLeastFor.toMillis() - elapsedTime))
                }
            }
        }
    }

    fun stop() {
        runBlocking {
            // check if it allows us to wait until all coroutines are done
            scope.coroutineContext[Job]?.cancelAndJoin()
        }
    }
}
