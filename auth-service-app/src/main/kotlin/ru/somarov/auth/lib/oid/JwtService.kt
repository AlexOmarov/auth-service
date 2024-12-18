package ru.somarov.auth.lib.oid

import com.auth0.jwt.interfaces.DecodedJWT

class JwtService(props: AuthProps) {
    private val handlers = mapOf(
        TokenType.ACCESS to JwtHandler(props.access),
        TokenType.REFRESH to JwtHandler(props.refresh),
        TokenType.USERID to JwtHandler(props.userid)
    )

    fun generate(userId: String, type: TokenType): String {
        return handlers[type]!!.generate(userId)
    }

    fun verify(token: String, type: TokenType): DecodedJWT? {
        return handlers[type]!!.verify(token)
    }

    enum class TokenType {
        ACCESS, REFRESH, USERID
    }
}
