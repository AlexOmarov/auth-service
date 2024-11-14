package ru.somarov.auth.lib.keydb

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.coroutines
import io.lettuce.core.codec.ByteArrayCodec
import java.io.Closeable

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class KeyDbClient(props: KeyDbProps) : Closeable {
    private val client = RedisClient.create(props.url)
    private val redis = client.connect(ByteArrayCodec()).coroutines()

    override fun close() {
        client.close()
    }

    suspend fun store(key: ByteArray, value: ByteArray) {
        redis.set(key, value)
    }

    suspend fun retrieve(key: ByteArray): ByteArray? {
        return redis.get(key)
    }
}
