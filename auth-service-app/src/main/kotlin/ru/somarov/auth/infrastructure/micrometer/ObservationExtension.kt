package ru.somarov.auth.infrastructure.micrometer

import io.micrometer.core.instrument.kotlin.asContextElement
import io.micrometer.observation.Observation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.withContext
import reactor.core.publisher.Mono

@Suppress("TooGenericExceptionCaught")
suspend fun <T> Observation.observeAndAwait(func: suspend () -> T) {
    start()
    try {
        withContext(openScope().use { observationRegistry.asContextElement() }) {
            func()
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
        mono(dispatcher + openScope().use { observationRegistry.asContextElement() }) {
            runnable()
        }
    } catch (error: Throwable) {
        error(error)
        throw error
    } finally {
        stop()
    }
}
