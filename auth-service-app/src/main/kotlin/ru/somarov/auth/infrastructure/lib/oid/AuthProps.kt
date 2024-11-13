package ru.somarov.auth.infrastructure.lib.oid

import io.ktor.server.application.*
import java.security.KeyFactory
import java.security.interfaces.RSAKey
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class AuthProps(val access: TokenConfig, val refresh: TokenConfig, val userid: TokenConfig) {
    data class TokenConfig(
        val key: RSAKey,
        val durationMillis: Long,
        val issuer: String,
        val audience: String
    )

    companion object {
        fun parse(env: ApplicationEnvironment): AuthProps {
            return AuthProps(
                access = loadTokenConfig("access", env),
                refresh = loadTokenConfig("refresh", env),
                userid = loadTokenConfig("userid", env)
            )
        }

        private fun loadTokenConfig(
            prefix: String,
            env: ApplicationEnvironment
        ): TokenConfig {
            return TokenConfig(
                durationMillis = env.config.property("ktor.auth.$prefix.duration-millis").getString().toLong(),
                key = loadRSAPrivateKey(env.config.property("ktor.auth.$prefix.key-path").getString(), env),
                issuer = env.config.property("ktor.auth.$prefix.issuer").getString(),
                audience = env.config.property("ktor.auth.$prefix.audience").getString(),
            )
        }

        @OptIn(ExperimentalEncodingApi::class)
        private fun loadRSAPrivateKey(path: String, environment: ApplicationEnvironment): RSAKey {
            val bytes = environment::class.java.classLoader.getResourceAsStream(path) ?: throw OidValidationException("")
            val content = bytes.bufferedReader().use { it.readText() }

            // Remove PEM headers and footers
            val keyPEM = content
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")  // Remove all whitespace

            // Decode the Base64 content
            val decodedKey = Base64.decode(keyPEM)

            val keySpec = PKCS8EncodedKeySpec(decodedKey)
            val keyFactory = KeyFactory.getInstance("RSA")
            return keyFactory.generatePrivate(keySpec) as RSAKey
        }
    }
}
