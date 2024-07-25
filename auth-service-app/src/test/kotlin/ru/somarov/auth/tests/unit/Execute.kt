package ru.somarov.auth.tests.unit

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import ru.somarov.auth.config.BaseIntegrationTest.env
import java.lang.management.ManagementFactory
import javax.management.ObjectName

fun execute(func: suspend (ApplicationTestBuilder) -> Unit) {
    testApplication {
        environment { config = config.mergeWith(ApplicationConfig("application.yaml")).mergeWith(env) }
        func(this)
    }
    ManagementFactory.getPlatformMBeanServer().unregisterMBean(
        ObjectName("io.r2dbc.pool:name=auth-service_pool,type=ConnectionPool")
    )
}
