package ru.somarov.auth.infrastructure.lib.oid

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import ru.somarov.auth.infrastructure.lib.oid.AuthProps.TokenConfig
import java.util.Date

class JwtHandler(private val config: TokenConfig) {
    private val logger = logger {  }

    private val algorithm = Algorithm.RSA256(config.key)

    fun generate(userId: String): String {
        return JWT.create()
            .withIssuer(config.issuer)
            .withAudience(config.audience)
            .withSubject(userId)
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + config.durationMillis))
            .sign(algorithm)
    }

    fun verify(accessToken: String): DecodedJWT? {
        return try {
            JWT.require(algorithm).build().verify(accessToken)
        } catch (e: JWTVerificationException) {
            logger.info { "Verification of jwt failed, reason - ${e.localizedMessage}" }
            null
        }
    }
}
