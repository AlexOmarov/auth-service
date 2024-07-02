package ru.somarov.auth.presentation.rsocket

import io.ktor.server.routing.Routing
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.readBytes
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.ktor.server.rSocket
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import ru.somarov.auth.application.service.Service
import ru.somarov.auth.presentation.request.AuthorizationRequest

internal fun Routing.authSocket(service: Service) {
    val handler = RSocketRequestHandler { requestResponse { respond(it, service) } }
    rSocket("login") { handler }
}

@OptIn(ExperimentalSerializationApi::class)
private suspend fun respond(payload: Payload, service: Service): Payload {
    val req = Cbor.Default.decodeFromByteArray<AuthorizationRequest>(payload.data.readBytes())
    val result = service.makeWork(req.userId.toString())
    return buildPayload { data { writePacket(ByteReadPacket(Cbor.Default.encodeToByteArray(result))) } }
}
