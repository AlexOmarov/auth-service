package ru.somarov.auth.application.service

import io.ktor.http.Parameters
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import ru.somarov.auth.lib.keydb.KeyDbClient
import ru.somarov.auth.lib.oid.AuthenticationContext
import ru.somarov.auth.lib.oid.AuthenticationRequest
import ru.somarov.auth.lib.oid.JwtService
import ru.somarov.auth.lib.oid.TokenRequest
import ru.somarov.auth.lib.util.generateRandomString
import ru.somarov.auth.presentation.request.LogoutRequest
import ru.somarov.auth.presentation.response.TokenResponse
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalSerializationApi::class)
class AuthenticationService(
    private val jwtService: JwtService,
    private val keyDbClient: KeyDbClient
) {
    suspend fun authorize(params: Parameters): String {
        val request = AuthenticationRequest.Companion.create(params)

        val authCode = generateRandomString()

        keyDbClient.store(
            Cbor.Default.encodeToByteArray(authCode),
            Cbor.Default.encodeToByteArray(request)
        )

        return authCode // TODO: Here i should redirect user to login page either in mobile or in web
    }

    // TODO: login function

    suspend fun logout(request: LogoutRequest) {
        keyDbClient.store(
            Cbor.encodeToByteArray<String>("revoked:${request.accessToken}"),
            Cbor.encodeToByteArray<String>(request.accessToken)
        )
        keyDbClient.store(
            Cbor.encodeToByteArray<String>("revoked:${request.refreshToken}"),
            Cbor.encodeToByteArray<String>(request.refreshToken)
        )
        request.idToken?.let {
            keyDbClient.store(Cbor.encodeToByteArray<String>("revoked:$it"), Cbor.encodeToByteArray<String>(it))
        }
    }

    @Suppress("ThrowsCount")
    suspend fun token(params: Parameters): TokenResponse {
        var request = TokenRequest.create(params)

        val authenticationBytes = keyDbClient.retrieve(Cbor.Default.encodeToByteArray("authentication:${request.code}"))
            ?: throw IllegalArgumentException("Invalid or expired authorization code")

        val authentication = Cbor.Default.decodeFromByteArray<AuthenticationContext>(authenticationBytes)
        if (authentication.request.clientId != request.clientId) {
            throw IllegalArgumentException("Invalid client_id")
        }

        if (!validate(authentication, request.codeVerifier)) {
            throw IllegalArgumentException("Invalid PKCE code_verifier")
        }

        val accessToken = jwtService.generate(authentication.userId, JwtService.TokenType.ACCESS)
        val refreshToken = jwtService.generate(authentication.userId, JwtService.TokenType.REFRESH)
        val userIdToken = if (authentication.request.scopes.contains("openid")) {
            jwtService.generate(authentication.userId, JwtService.TokenType.USERID)
        } else {
            null
        }

        return TokenResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            idToken = userIdToken
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun validate(context: AuthenticationContext, codeVerifier: String): Boolean {
        return when (context.request.codeChallengeMethod) {
            AuthenticationRequest.CodeChallengeMethod.S256 -> {
                val digest = MessageDigest.getInstance("SHA-256")
                val base64EncodedVerifier = Base64.encode(digest.digest(codeVerifier.toByteArray()))
                base64EncodedVerifier == context.request.codeChallenge
            }

            AuthenticationRequest.CodeChallengeMethod.PLAIN -> codeVerifier == context.request.codeChallenge
        }
    }
}
