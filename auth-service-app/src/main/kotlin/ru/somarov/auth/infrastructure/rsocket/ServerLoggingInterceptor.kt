package ru.somarov.auth.infrastructure.rsocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.core.readBytes
import io.netty.buffer.ByteBufUtil.isText
import io.netty.buffer.Unpooled
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.core.Interceptor
import io.rsocket.kotlin.payload.Payload
import io.rsocket.metadata.CompositeMetadata
import io.rsocket.metadata.WellKnownMimeType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext

class ServerLoggingInterceptor : Interceptor<RSocket> {

    private val logger = KtorSimpleLogger(this.javaClass.name)
    private val encoding: Charset = Charset.forName("UTF8")
    private var cborMapper = ObjectMapper(CBORFactory()).registerKotlinModule()

    override fun intercept(input: RSocket): RSocket {
        return object : RSocket {

            override val coroutineContext: CoroutineContext
                get() = input.coroutineContext

            override suspend fun requestResponse(payload: Payload): Payload {
                val deserializedRequest = getDeserializedPayload(payload)
                logger.info(
                    "Incoming rsocket request -> ${deserializedRequest.third}: " +
                        "payload: ${deserializedRequest.first}, metadata: ${deserializedRequest.second}"
                )

                val response = super.requestResponse(payload)

                val deserializedResponse = getDeserializedPayload(response)
                logger.info(
                    "Outgoing rsocket response <- ${deserializedRequest.third}: " +
                        "payload: ${deserializedResponse.first}, " +
                        "request metadata: ${deserializedRequest.second}, " +
                        "response metadata: ${deserializedResponse.second}"
                )
                return response
            }
        }
    }

    private fun getDeserializedPayload(payload: Payload): Triple<Any, List<String>, String> {
        val data = try {
            val array = payload.data.copy().readBytes()
            if (array.isNotEmpty()) {
                val map = cborMapper.readValue(array, Any::class.java)
                Json.Default.encodeToString(map)
            } else {
                "Body is null"
            }
        } catch (e: SerializationException) {
            logger.error("Got error while deserializing cbor to string", e)
            "Body is null"
        }
        var routing = "null"
        val metadata = payload.metadata?.copy()?.let {
            CompositeMetadata(Unpooled.wrappedBuffer(it.readBytes()), false)
                .map { met ->
                    val content = if (isText(met.content, encoding)) met.content.toString(encoding) else "Not text"
                    if (met.mimeType == WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.string) {
                        routing = if (content.isNotEmpty()) content.substring(1) else content
                        "Header(mime: ${met.mimeType}, content: $routing)"
                    } else {
                        "Header(mime: ${met.mimeType}, content: $content)"
                    }
                }
        } ?: listOf()

        return Triple(data, metadata, routing)
    }
}
