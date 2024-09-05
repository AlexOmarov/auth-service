package ru.somarov.auth.infrastructure.props

import io.ktor.server.application.*

data class AppProps(
    val name: String,
    val instance: String,
    val db: DbProps,
    val kafka: KafkaProps,
    val otel: OtelProps
) {

    companion object {
        fun parse(environment: ApplicationEnvironment): AppProps {
            return AppProps(
                name = environment.config.property("ktor.name").getString(),
                instance = environment.config.property("ktor.instance").getString(),
                db = DbProps.parse(environment),
                kafka = KafkaProps.parse(environment),
                otel = OtelProps.parse(environment)
            )
        }
    }
}
