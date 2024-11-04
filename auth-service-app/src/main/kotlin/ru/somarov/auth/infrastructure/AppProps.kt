package ru.somarov.auth.infrastructure

import io.ktor.server.application.*
import ru.somarov.auth.infrastructure.lib.db.DbProps
import ru.somarov.auth.infrastructure.lib.kafka.KafkaProps
import ru.somarov.auth.infrastructure.lib.observability.props.OtelProps

data class AppProps(
    val db: DbProps,
    val kafka: KafkaProps,
    val monitoring: OtelProps
) {

    companion object {
        fun parse(environment: ApplicationEnvironment): AppProps {
            return AppProps(
                db = DbProps.Companion.parse(environment),
                kafka = KafkaProps.parse(environment),
                monitoring = OtelProps.parse(environment)
            )
        }
    }
}
