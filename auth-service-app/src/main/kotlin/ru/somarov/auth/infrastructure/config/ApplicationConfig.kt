package ru.somarov.auth.infrastructure.config

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ServerReady
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing
import io.rsocket.kotlin.core.WellKnownMimeType
import io.rsocket.kotlin.ktor.client.RSocketSupport
import io.rsocket.kotlin.ktor.client.rSocket
import io.rsocket.kotlin.payload.PayloadMimeType
import kotlinx.coroutines.runBlocking
import ru.somarov.auth.application.service.Service
import ru.somarov.auth.infrastructure.db.DatabaseClient
import ru.somarov.auth.infrastructure.db.repo.ClientRepo
import ru.somarov.auth.infrastructure.db.repo.RevokedAuthorizationRepo
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
    val revokedAuthorizationRepo = RevokedAuthorizationRepo(dbClient)
    val service = Service(repo, revokedAuthorizationRepo)

    val scheduler = Scheduler(dbClient.factory, observationRegistry)
    val consumer = MailConsumer(service, props.kafka, observationRegistry)
    val retryConsumer = RetryConsumer(props.kafka, listOf(consumer), observationRegistry)

    setupPipeline(this)
    setupRsocket(this, meterRegistry, observationRegistry)

    routing {
        openAPI("openapi")
        swaggerUI("swagger")

        authSocket(service)
    }

    environment.monitor.subscribe(ServerReady) {
        val client = runBlocking {
            HttpClient(CIO) {
                install(WebSockets)
                install(RSocketSupport) {
                    connector {
                        connectionConfig {
                            payloadMimeType = PayloadMimeType(
                                data = WellKnownMimeType.ApplicationJson,
                                metadata = WellKnownMimeType.MessageRSocketCompositeMetadata
                            )
                        }
                    }
                }
            }.rSocket(path = "login", port = 9099)
        }
        registerTasks(scheduler, client)
        scheduler.start()
    }

    environment.monitor.subscribe(ApplicationStopped) {
        scheduler.stop()
        consumer.stop()
        retryConsumer.stop()
    }
}
