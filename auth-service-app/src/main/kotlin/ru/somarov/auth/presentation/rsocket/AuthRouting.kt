package ru.somarov.auth.presentation.rsocket

import io.ktor.server.routing.Routing
import io.ktor.utils.io.core.writeText
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.ktor.server.rSocket
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.somarov.auth.application.service.Service
import ru.somarov.auth.presentation.request.AuthorizationRequest

internal fun Routing.authSocket(service: Service) {
    val handler = RSocketRequestHandler { requestResponse { respond(it, service) } }
    rSocket("login") { handler }
}

private suspend fun respond(payload: Payload, service: Service): Payload {
    val req = Json.Default.decodeFromString<AuthorizationRequest>(payload.data.readText())
    val result = service.makeWork(req.userId.toString())
    return buildPayload { data { writeText(Json.Default.encodeToString(result)) } }
}
