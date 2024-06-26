package ru.somarov.auth.infrastructure.config

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing
import ru.somarov.auth.application.service.SchedulerService
import ru.somarov.auth.application.service.Service
import ru.somarov.auth.infrastructure.db.DatabaseClient
import ru.somarov.auth.infrastructure.db.repo.ClientRepo
import ru.somarov.auth.infrastructure.props.parseProps
import ru.somarov.auth.infrastructure.scheduler.Scheduler
import ru.somarov.auth.presentation.consumers.MailConsumer
import ru.somarov.auth.presentation.consumers.RetryConsumer
import ru.somarov.auth.presentation.rsocket.authSocket
import ru.somarov.auth.presentation.scheduler.registerTasks
import java.util.TimeZone

@Suppress("unused") // Referenced in application.yaml
internal fun Application.config() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    val props = parseProps(environment)

    val (meterRegistry, observationRegistry) = setupObservability(this, props)

    val dbClient = DatabaseClient(props, meterRegistry)
    val repo = ClientRepo(dbClient)
    val service = Service(repo)
    val schedulerService = SchedulerService(repo)

    val scheduler = Scheduler(dbClient.factory)
    val consumer = MailConsumer(service, props.kafka, observationRegistry)
    val retryConsumer = RetryConsumer(props.kafka, listOf(consumer), observationRegistry)

    setupPipeline(this)
    setupHttp(this, observationRegistry)
    setupRsocket(this, meterRegistry, observationRegistry)
    registerTasks(scheduler, schedulerService)

    routing {
        openAPI("openapi")
        swaggerUI("swagger")

        authSocket(service)
    }

    environment.monitor.subscribe(ApplicationStarted) {
        consumer.start()
        scheduler.start()
        retryConsumer.start()
    }

    environment.monitor.subscribe(ApplicationStopped) {
        scheduler.stop()
        consumer.stop()
        retryConsumer.stop()
    }
}
