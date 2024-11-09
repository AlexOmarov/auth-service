package ru.somarov.auth.infrastructure.lib.oid

import io.ktor.server.application.*
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyFactory
import java.security.interfaces.RSAKey
import java.security.spec.PKCS8EncodedKeySpec

data class AuthProps(val access: TokenConfig, val refresh: TokenConfig, val oid: TokenConfig) {
    data class TokenConfig(val key: RSAKey, val durationMillis: Long)

    companion object {
        fun parse(env: ApplicationEnvironment): AuthProps {
            return AuthProps(
                access = loadTokenConfig("ktor.auth.access.duration-millis", "ktor.auth.access.key-path", env),
                refresh = loadTokenConfig("ktor.auth.refresh.duration-millis", "ktor.auth.refresh.key-path", env),
                oid = loadTokenConfig("ktor.auth.oid.duration-millis", "ktor.auth.oid.key-path", env)
            )
        }

        private fun loadTokenConfig(
            durationProperty: String,
            keyPathProperty: String,
            environment: ApplicationEnvironment
        ): TokenConfig {
            return TokenConfig(
                durationMillis = environment.config.property(durationProperty).getString()
                    .toLong(),
                key = loadRSAPrivateKey(environment.config.property(keyPathProperty).getString())

            )
        }

        private fun loadRSAPrivateKey(path: String): RSAKey {
            val privateKeyPath = Paths.get(path)
            val privateKeyBytes = Files.readAllBytes(privateKeyPath)
            val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            return keyFactory.generatePrivate(keySpec) as RSAKey
        }
    }
}
