package ru.somarov.auth.infrastructure.rsocket

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.observation.ObservationRegistry
import io.rsocket.core.RSocketClient
import io.rsocket.core.RSocketConnector
import io.rsocket.core.Resume
import io.rsocket.loadbalance.LoadbalanceRSocketClient
import io.rsocket.loadbalance.LoadbalanceTarget
import io.rsocket.micrometer.MicrometerRSocketInterceptor
import io.rsocket.micrometer.observation.ObservationRequesterRSocketProxy
import io.rsocket.plugins.RSocketInterceptor
import io.rsocket.transport.ClientTransport
import reactor.core.publisher.Flux
import reactor.util.retry.Retry
import reactor.util.retry.RetryBackoffSpec
import java.time.Duration
import java.util.*

object ClientFactory {

    private const val RECONNECT_ATTEMPTS = 5L
    private const val RECONNECT_DELAY = 1L
    private const val RECONNECT_JITTER = 1.0

    private const val RESUME_ATTEMPTS = 5L
    private const val RESUME_DELAY = 1L
    private const val RESUME_JITTER = 1.0

    private const val KEEPALIVE_INTERVAL = 3L
    private const val KEEPALIVE_MAX_LIFETIME = 8L

    fun create(config: Config, meterRegistry: MeterRegistry, observationRegistry: ObservationRegistry): RSocketClient {
        return LoadbalanceRSocketClient.builder {
            Flux.just(LoadbalanceTarget.from(UUID.randomUUID().toString(), config.transport)).repeat()
        }.connector(
            RSocketConnector.create().also { connector ->

                connector.interceptors {
                    it.forRequester(ClientLoggingInterceptor())
                    it.forRequester(MicrometerRSocketInterceptor(meterRegistry))
                    it.forRequester(RSocketInterceptor { ObservationRequesterRSocketProxy(it, observationRegistry) })
                }

                connector.resume(Resume().also { it.retry(config.resumption.retry) })

                connector.reconnect(config.reconnect.retry)


                connector.keepAlive(
                    Duration.ofMillis(config.keepAlive.interval),
                    Duration.ofMillis(config.keepAlive.maxLifeTime)
                )
            }
        ).weightedLoadbalanceStrategy().build()
    }

    data class Config(
        val transport: ClientTransport,
        val resumption: ResumptionConfig = ResumptionConfig(),
        val reconnect: ReconnectConfig = ReconnectConfig(),
        val keepAlive: KeepAliveConfig = KeepAliveConfig(),
    )

    data class ReconnectConfig(
        val attempts: Long = RECONNECT_ATTEMPTS,
        val delay: Long = RECONNECT_DELAY,
        val jitter: Double = RECONNECT_JITTER
    ) {
        val retry: RetryBackoffSpec = Retry.fixedDelay(attempts, Duration.ofMillis(delay)).jitter(jitter)
    }

    data class ResumptionConfig(
        val attempts: Long = RESUME_ATTEMPTS,
        val delay: Long = RESUME_DELAY,
        val jitter: Double = RESUME_JITTER
    ) {
        val retry: RetryBackoffSpec = Retry.fixedDelay(attempts, Duration.ofMillis(delay)).jitter(jitter)
    }

    data class KeepAliveConfig(
        val interval: Long = KEEPALIVE_INTERVAL,
        val maxLifeTime: Long = KEEPALIVE_MAX_LIFETIME
    )
}
