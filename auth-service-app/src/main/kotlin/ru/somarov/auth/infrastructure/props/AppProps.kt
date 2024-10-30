package ru.somarov.auth.infrastructure.props

import io.ktor.server.application.*
import ru.somarov.auth.infrastructure.lib.db.DbProps
import ru.somarov.auth.infrastructure.lib.kafka.KafkaProps
import ru.somarov.auth.infrastructure.lib.observability.props.OtelProps

data class AppProps(
    val name: String,
    val instance: String,
    val db: DbProps,
    val kafka: KafkaProps,
    val monitoring: OtelProps
) {

    companion object {
        fun parse(environment: ApplicationEnvironment): AppProps {
            return AppProps(
                name = environment.config.property("ktor.name").getString(),
                instance = environment.config.property("ktor.instance").getString(),
                db = DbProps.Companion.parse(environment),
                kafka = KafkaProps.parse(environment),
                monitoring = OtelProps.parse(environment)
            )
        }
    }
}
