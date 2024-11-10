package ru.somarov.auth.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.somarov.auth.infrastructure.lib.oid.JwtService
import ru.somarov.auth.presentation.request.ValidationRequest

class ValidationService(private val jwtService: JwtService) {

    private val logger = KotlinLogging.logger { }

    fun validate(token: String, type: ValidationRequest.TokenType): Boolean {
        return jwtService.verify(token, getTokenType(type)) != null
    }

    private fun getTokenType(type: ValidationRequest.TokenType): JwtService.TokenType {
        return when (type) {
            ValidationRequest.TokenType.ACCESS -> JwtService.TokenType.ACCESS
            ValidationRequest.TokenType.REFRESH -> JwtService.TokenType.REFRESH
            ValidationRequest.TokenType.USER_ID -> JwtService.TokenType.USERID
        }
    }
}
