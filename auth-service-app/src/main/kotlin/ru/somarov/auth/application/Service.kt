package ru.somarov.auth.application

import io.ktor.server.application.ApplicationEnvironment
import io.ktor.util.logging.KtorSimpleLogger

internal class Service(private val env: ApplicationEnvironment) {
    private val logger = KtorSimpleLogger("ru.somarov.auth.application.Service")
    fun makeWork() {
        val name = env.config.property("application.name").getString()
        logger.info("hi sfdgs $name")
    }
}
