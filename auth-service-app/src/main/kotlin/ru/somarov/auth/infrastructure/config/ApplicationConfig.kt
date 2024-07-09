package ru.somarov.auth.infrastructure.config

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ServerReady
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing
import ru.somarov.auth.application.service.Service
import ru.somarov.auth.infrastructure.db.DatabaseClient
import ru.somarov.auth.infrastructure.db.repo.ClientRepo
import ru.somarov.auth.infrastructure.db.repo.RevokedAuthorizationRepo
import ru.somarov.auth.infrastructure.props.AppProps
import ru.somarov.auth.infrastructure.scheduler.Scheduler
import ru.somarov.auth.presentation.rsocket.authSocket
import ru.somarov.auth.presentation.scheduler.registerTasks
import java.util.TimeZone

@Suppress("unused") // Referenced in application.yaml
internal fun Application.config() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    val props = AppProps.parseProps(environment)

    val (meterRegistry, observationRegistry) = setupObservability(this, props)

    val dbClient = DatabaseClient(props, meterRegistry)
    val repo = ClientRepo(dbClient)
    val revokedAuthorizationRepo = RevokedAuthorizationRepo(dbClient)
    val service = Service(repo, revokedAuthorizationRepo)

    val scheduler = Scheduler(dbClient.factory, observationRegistry)

    setupPipeline(this)
    setupRsocket(this, meterRegistry, observationRegistry)

    routing {
        openAPI("openapi")
        swaggerUI("swagger")

        authSocket(service)
    }

    environment.monitor.subscribe(ServerReady) {
        registerTasks(scheduler)
        scheduler.start()
    }

    environment.monitor.subscribe(ApplicationStopped) {
        scheduler.stop()
    }
}
