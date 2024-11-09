package ru.somarov.auth.infrastructure.lib.oid

import com.auth0.jwt.interfaces.DecodedJWT

class JwtService(props: AuthProps) {
    private val handlers = mapOf(
        KeyType.ACCESS to JwtHandler(props.access),
        KeyType.REFRESH to JwtHandler(props.refresh),
        KeyType.USERID to JwtHandler(props.oid)
    )

    fun generate(userId: String, type: KeyType): String {
        return handlers[type]!!.generate(userId)
    }

    fun verify(token: String, type: KeyType): DecodedJWT? {
        return handlers[type]!!.verify(token)
    }

    enum class KeyType {
        ACCESS, REFRESH, USERID
    }
}
