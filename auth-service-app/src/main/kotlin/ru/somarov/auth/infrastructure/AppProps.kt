package ru.somarov.auth.infrastructure

import io.ktor.server.application.ApplicationEnvironment
import ru.somarov.auth.lib.db.DbProps
import ru.somarov.auth.lib.oid.AuthProps
import ru.somarov.auth.lib.kafka.KafkaProps
import ru.somarov.auth.lib.keydb.KeyDbProps
import ru.somarov.auth.lib.observability.props.OtelProps

data class AppProps(
    val db: DbProps,
    val kafka: KafkaProps,
    val cache: KeyDbProps,
    val auth: AuthProps,
    val monitoring: OtelProps
) {

    companion object {
        fun parse(environment: ApplicationEnvironment): AppProps {
            return AppProps(
                db = DbProps.Companion.parse(environment),
                kafka = KafkaProps.parse(environment),
                cache = KeyDbProps.parse(environment),
                auth = AuthProps.parse(environment),
                monitoring = OtelProps.parse(environment)
            )
        }
    }
}
