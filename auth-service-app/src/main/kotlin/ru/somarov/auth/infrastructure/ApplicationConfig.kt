package ru.somarov.auth.infrastructure

import io.ktor.serialization.kotlinx.cbor.cbor
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.rsocket.kotlin.ktor.server.RSocketSupport
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import ru.somarov.auth.application.Service
import ru.somarov.auth.infrastructure.rsocket.ServerInterceptor
import ru.somarov.auth.presentation.auth
import ru.somarov.auth.presentation.authSocket

@Suppress("unused") // Referenced in application.yaml
@OptIn(ExperimentalSerializationApi::class)
internal fun Application.config() {

    install(ContentNegotiation) { cbor(Cbor { ignoreUnknownKeys = true }) }
    install(WebSockets)
    install(RSocketSupport) { server { interceptors { forResponder(ServerInterceptor()) } } }

    val service = Service(environment)

    routing {
        openAPI("openapi")
        swaggerUI("swagger")

        auth(service)
        authSocket(service)
    }
}
