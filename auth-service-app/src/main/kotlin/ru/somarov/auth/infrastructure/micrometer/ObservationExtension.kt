package ru.somarov.auth.infrastructure.micrometer

import io.micrometer.core.instrument.kotlin.asContextElement
import io.micrometer.observation.Observation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.withContext
import reactor.core.publisher.Mono

@Suppress("TooGenericExceptionCaught")
suspend fun <T> Observation.observeSuspended(runnable: suspend () -> T) {
    start()
    try {
        openScope().use {
            withContext(currentCoroutineContext() + this.observationRegistry.asContextElement()) {
                runnable()
            }
        }
    } catch (error: Throwable) {
        error(error)
        throw error
    } finally {
        stop()
    }
}

@Suppress("TooGenericExceptionCaught")
fun <T> Observation.observeSuspendedMono(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    runnable: suspend () -> T
): Mono<T> {
    start()
    return try {
        openScope().use {
            mono(dispatcher + this.observationRegistry.asContextElement()) {
                runnable()
            }
        }
    } catch (error: Throwable) {
        error(error)
        throw error
    } finally {
        stop()
    }
}
