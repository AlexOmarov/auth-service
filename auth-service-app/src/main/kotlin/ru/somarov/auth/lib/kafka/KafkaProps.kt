package ru.somarov.auth.lib.kafka

import io.ktor.server.application.*

data class KafkaProps(
    val brokers: String,
    val producers: KafkaProducersProps,
) {
    data class KafkaProducersProps(
        val registration: KafkaProducerProps,
        val auth: KafkaProducerProps,
        val dlq: KafkaProducerProps,
        val retry: KafkaProducerProps
    )

    data class KafkaProducerProps(
        val enabled: Boolean,
        val topic: String,
        val maxInFlight: Int
    )

    companion object {
        fun parse(environment: ApplicationEnvironment): KafkaProps {
            return KafkaProps(
                brokers = environment.config.property("ktor.kafka.brokers").getString(),
                producers = KafkaProducersProps(
                    dlq = KafkaProducerProps(
                        enabled = environment.config.property("ktor.kafka.producers.retry.enabled")
                            .getString().toBoolean(),
                        topic = environment.config.property("ktor.kafka.producers.retry.topic")
                            .getString(),
                        maxInFlight = environment.config
                            .property("ktor.kafka.producers.retry.max-in-flight").getString().toInt()
                    ),
                    retry = KafkaProducerProps(
                        enabled = environment.config.property("ktor.kafka.producers.dlq.enabled")
                            .getString().toBoolean(),
                        topic = environment.config.property("ktor.kafka.producers.dlq.topic")
                            .getString(),
                        maxInFlight = environment.config.property("ktor.kafka.producers.dlq.max-in-flight")
                            .getString().toInt()

                    ),
                    registration = KafkaProducerProps(
                        enabled = environment.config.property("ktor.kafka.producers.registration.enabled")
                            .getString().toBoolean(),
                        topic = environment.config.property("ktor.kafka.producers.registration.topic")
                            .getString(),
                        maxInFlight = environment.config.property("ktor.kafka.producers.registration.max-in-flight")
                            .getString().toInt()

                    ),
                    auth = KafkaProducerProps(
                        enabled = environment.config.property("ktor.kafka.producers.auth.enabled")
                            .getString().toBoolean(),
                        topic = environment.config.property("ktor.kafka.producers.auth.topic")
                            .getString(),
                        maxInFlight = environment.config.property("ktor.kafka.producers.auth.max-in-flight")
                            .getString().toInt()

                    )
                )
            )
        }
    }
}
