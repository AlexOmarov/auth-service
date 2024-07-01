package ru.somarov.auth.infrastructure

import io.ktor.server.routing.Routing
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.core.writeText
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.ktor.server.rSocket
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

internal fun Routing.authSocket() {
    val handler = RSocketRequestHandler { requestResponse {
        KtorSimpleLogger("WOPW").info("!!!!!!!!!!!!!!!")
        respond()
    } }
    rSocket("login") { handler }
}

private fun respond(): Payload {
    return buildPayload { data { writeText(Json.Default.encodeToString(AuthorizationRequest(UUID.randomUUID()))) } }
}