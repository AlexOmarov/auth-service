package ru.somarov.auth.infrastructure.rsocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.netty.buffer.ByteBufUtil.isText
import io.netty.buffer.Unpooled
import io.rsocket.Payload
import io.rsocket.metadata.CompositeMetadata
import io.rsocket.metadata.WellKnownMimeType
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.charset.Charset

object Util {

    private var cborMapper = ObjectMapper(CBORFactory()).registerKotlinModule()
    private var jsonMapper = ObjectMapper().registerKotlinModule()

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val encoding: Charset = Charset.forName("UTF8")

    fun getDeserializedPayload(payload: Payload): Triple<Any, List<String>, String> {
        val data = try {
            val array = getByteArray(payload.data)
            if (array.isNotEmpty()) {
                val map = cborMapper.readValue(getByteArray(payload.data), Any::class.java)
                jsonMapper.writeValueAsString(map)
            } else {
                "null"
            }
        } catch (e: SerializationException) {
            log.error("Got error while deserializing cbor to string", e)
            payload.dataUtf8
        }
        var routing = "null"
        val metadata = CompositeMetadata(Unpooled.wrappedBuffer(payload.metadata), false)
            .map {
                val content = if (isText(it.content, encoding)) it.content.toString(encoding) else "Not text"
                if (it.mimeType == WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.string) {
                    routing = if (content.isNotEmpty()) content.substring(1) else content
                    "Header(mime: ${it.mimeType}, content: $routing)"
                } else {
                    "Header(mime: ${it.mimeType}, content: $content)"
                }
            }

        return Triple(data, metadata, routing)
    }

    @Suppress("kotlin:S6518") // Here byteArray should be filled with get method
    private fun getByteArray(buffer: ByteBuffer): ByteArray {
        val byteArray = ByteArray(buffer.remaining())
        buffer.get(byteArray)
        buffer.rewind()
        return byteArray
    }
}
