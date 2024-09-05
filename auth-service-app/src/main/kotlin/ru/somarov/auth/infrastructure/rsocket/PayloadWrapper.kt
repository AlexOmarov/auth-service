package ru.somarov.auth.infrastructure.rsocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufUtil.isText
import io.netty.buffer.Unpooled
import io.rsocket.Payload
import io.rsocket.kotlin.ExperimentalMetadataApi
import io.rsocket.kotlin.internal.BufferPool
import io.rsocket.kotlin.metadata.CompositeMetadata.Reader.read
import io.rsocket.metadata.CompositeMetadata
import io.rsocket.metadata.CompositeMetadataCodec
import io.rsocket.metadata.WellKnownMimeType
import io.rsocket.util.DefaultPayload
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.charset.Charset
import io.rsocket.kotlin.payload.Payload as payload

class PayloadWrapper(
    private val payload: Payload,
    private val cborMapper: ObjectMapper = ObjectMapper(CBORFactory()).registerKotlinModule(),
    private val jsonMapper: ObjectMapper = ObjectMapper().registerKotlinModule(),
    private val encoding: Charset = Charset.forName("UTF8"),
) : Payload {
    private val log = LoggerFactory.getLogger(this.javaClass)

    fun deserialize(): Triple<Any, List<String>, String> {
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

    suspend fun toKotlinPayload(): io.rsocket.kotlin.payload.Payload {
        return payload(
            ByteReadChannel(payload.data).readRemaining(),
            ByteReadChannel(payload.metadata).readRemaining()
        )
    }

    override fun refCnt(): Int {
        return payload.refCnt()
    }

    override fun retain(): Payload {
        return payload.retain()
    }

    override fun retain(increment: Int): Payload {
        return payload.retain(increment)
    }

    override fun touch(): Payload {
        return payload.touch()
    }

    override fun touch(hint: Any): Payload {
        return payload.touch(hint)
    }

    override fun release(): Boolean {
        return payload.release()
    }

    override fun release(p0: Int): Boolean {
        return payload.release(p0)
    }

    override fun hasMetadata(): Boolean {
        return payload.hasMetadata()
    }

    override fun sliceMetadata(): ByteBuf {
        return payload.sliceMetadata()
    }

    override fun sliceData(): ByteBuf {
        return payload.sliceData()
    }

    override fun data(): ByteBuf {
        return payload.data()
    }

    override fun metadata(): ByteBuf {
        return payload.metadata()
    }

    @Suppress("kotlin:S6518") // Here byteArray should be filled with get method
    private fun getByteArray(buffer: ByteBuffer): ByteArray {
        val byteArray = ByteArray(buffer.remaining())
        buffer.get(byteArray)
        buffer.rewind()
        return byteArray
    }

    companion object {
        @OptIn(ExperimentalMetadataApi::class)
        fun fromKotlinPayload(payload: io.rsocket.kotlin.payload.Payload): PayloadWrapper {
            val metadata = ByteBufAllocator.DEFAULT.compositeBuffer()
            payload.metadata?.copy()?.read(BufferPool.Default)?.entries?.forEach {
                CompositeMetadataCodec.encodeAndAddMetadata(
                    metadata,
                    ByteBufAllocator.DEFAULT,
                    it.mimeType.toString(),
                    Unpooled.wrappedBuffer(it.content.readBytes())
                )
            }
            val defPayload = DefaultPayload.create(
                Unpooled.wrappedBuffer(payload.data.readBytes()),
                metadata
            )
            return PayloadWrapper(defPayload)
        }
    }
}
