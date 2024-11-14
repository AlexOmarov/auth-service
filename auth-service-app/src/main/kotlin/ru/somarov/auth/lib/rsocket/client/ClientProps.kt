package ru.somarov.auth.lib.rsocket.client

import io.ktor.server.application.ApplicationEnvironment

data class ClientProps(
    val host: String
) {
    companion object {
        fun parse(environment: ApplicationEnvironment, prefix: String): ClientProps {
            return ClientProps(
                environment.config.property("$prefix.host").getString()
            )
        }
    }
}
