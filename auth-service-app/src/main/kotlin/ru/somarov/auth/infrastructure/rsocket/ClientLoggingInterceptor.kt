package ru.somarov.auth.infrastructure.rsocket

import io.rsocket.Payload
import io.rsocket.RSocket
import io.rsocket.plugins.RSocketInterceptor
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import ru.somarov.auth.infrastructure.rsocket.Util.getDeserializedPayload

class ClientLoggingInterceptor : RSocketInterceptor {
    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun apply(rSocket: RSocket): RSocket {
        return object : RSocket {
            override fun requestResponse(payload: Payload): Mono<Payload> {
                val deserializedRequest = getDeserializedPayload(payload)
                log.info(
                    "Outgoing rsocket request <- ${deserializedRequest.third}: " +
                        "payload: ${deserializedRequest.first}, metadata: ${deserializedRequest.second}"
                )
                return rSocket.requestResponse(payload)
                    .doOnSuccess {
                        val deserializedResponse = getDeserializedPayload(it)
                        log.info(
                            "Incoming rsocket response -> ${deserializedRequest.third}: " +
                                "payload: ${deserializedResponse.first}, " +
                                "request metadata: ${deserializedRequest.second}, " +
                                "response metadata: ${deserializedResponse.second}"
                        )
                    }
            }
        }
    }
}
