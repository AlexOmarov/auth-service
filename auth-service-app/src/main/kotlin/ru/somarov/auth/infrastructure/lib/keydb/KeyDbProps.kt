package ru.somarov.auth.infrastructure.lib.keydb

import io.ktor.server.application.ApplicationEnvironment

data class KeyDbProps(
    val url: String
) {
    companion object {
        fun parse(env: ApplicationEnvironment): KeyDbProps {
            return KeyDbProps(
                url = env.config.property("ktor.cache.url").getString()
            )
        }
    }
}
