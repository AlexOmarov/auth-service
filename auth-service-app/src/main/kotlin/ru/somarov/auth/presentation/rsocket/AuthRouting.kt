package ru.somarov.auth.presentation.rsocket

import io.ktor.server.routing.*
import io.ktor.utils.io.core.*
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.ktor.server.rSocket
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import ru.somarov.auth.application.service.Service
import ru.somarov.auth.presentation.request.RegistrationRequest
import ru.somarov.auth.presentation.request.ValidationRequest
import ru.somarov.auth.presentation.response.RegistrationResponse
import ru.somarov.auth.presentation.response.ValidationResponse

@OptIn(ExperimentalSerializationApi::class)
internal fun Routing.authSocket(service: Service) {
    rSocket("validate") {
        RSocketRequestHandler {
            requestResponse {
                val req = Cbor.Default.decodeFromByteArray<ValidationRequest>(it.data.readBytes())
                val result = service.validate(req)
                buildPayload {
                    data { writePacket(ByteReadPacket(Cbor.Default.encodeToByteArray(ValidationResponse(result)))) }
                }
            }
        }
    }

    rSocket("register") {
        RSocketRequestHandler {
            requestResponse {
                val req = Cbor.Default.decodeFromByteArray<RegistrationRequest>(it.data.readBytes())
                val result = service.register(req)
                buildPayload {
                    data { writePacket(ByteReadPacket(Cbor.Default.encodeToByteArray(RegistrationResponse(result)))) }
                }
            }
        }
    }
}
