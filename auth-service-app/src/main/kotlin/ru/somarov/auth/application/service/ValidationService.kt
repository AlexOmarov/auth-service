package ru.somarov.auth.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import ru.somarov.auth.infrastructure.lib.keydb.KeyDbClient
import ru.somarov.auth.infrastructure.lib.oid.JwtService
import ru.somarov.auth.presentation.request.ValidationRequest

class ValidationService(private val jwtService: JwtService, private val cache: KeyDbClient) {

    private val logger = KotlinLogging.logger { }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun validate(token: String, type: ValidationRequest.TokenType): Boolean {
        val cachedRevocation = cache.retrieve(Cbor.encodeToByteArray<String>("revoked:$token"))
        return cachedRevocation == null && jwtService.verify(token, getTokenType(type)) != null
    }

    private fun getTokenType(type: ValidationRequest.TokenType): JwtService.TokenType {
        return when (type) {
            ValidationRequest.TokenType.ACCESS -> JwtService.TokenType.ACCESS
            ValidationRequest.TokenType.REFRESH -> JwtService.TokenType.REFRESH
            ValidationRequest.TokenType.USER_ID -> JwtService.TokenType.USERID
        }
    }
}
