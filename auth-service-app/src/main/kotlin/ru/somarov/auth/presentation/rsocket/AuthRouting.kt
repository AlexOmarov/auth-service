package ru.somarov.auth.presentation.rsocket

import io.ktor.server.routing.*
import io.ktor.utils.io.core.*
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.ktor.server.rSocket
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.io.readByteArray
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import ru.somarov.auth.application.service.AuthenticationService
import ru.somarov.auth.presentation.request.RegistrationRequest
import ru.somarov.auth.presentation.request.ValidationRequest
import ru.somarov.auth.presentation.response.RegistrationResponse
import ru.somarov.auth.presentation.response.ValidationResponse

@OptIn(ExperimentalSerializationApi::class)
internal fun Routing.authSocket(authenticationService: AuthenticationService) {

    rSocket("validate") {
        RSocketRequestHandler {
            requestResponse {
                val req = Cbor.Default.decodeFromByteArray<ValidationRequest>(it.data.readByteArray())
                val result = authenticationService.validate(req)
                buildPayload {
                    data { writePacket(ByteReadPacket(Cbor.Default.encodeToByteArray(ValidationResponse(result)))) }
                }
            }
        }
    }

    rSocket("register") {
        RSocketRequestHandler {
            requestResponse {
                val req = Cbor.Default.decodeFromByteArray<RegistrationRequest>(it.data.readByteArray())
                val result = authenticationService.register(req)
                buildPayload {
                    data { writePacket(ByteReadPacket(Cbor.Default.encodeToByteArray(RegistrationResponse(result)))) }
                }
            }

            requestStream {
                val req = Cbor.Default.decodeFromByteArray<RegistrationRequest>(it.data.readByteArray())
                val result = authenticationService.register(req)
                val response = Cbor.Default.encodeToByteArray(RegistrationResponse(result))
                flow {
                    repeat(REPEAT) {
                        delay(DELAY)
                        emit(buildPayload { data { writePacket(buildPacket { writeFully(response) }) } })
                    }
                }
            }
        }
    }
}

const val REPEAT = 10
const val DELAY = 2000L
