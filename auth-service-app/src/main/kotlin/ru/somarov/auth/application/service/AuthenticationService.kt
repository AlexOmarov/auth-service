package ru.somarov.auth.application.service

import io.ktor.http.Parameters
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import ru.somarov.auth.infrastructure.lib.keydb.KeyDbClient
import ru.somarov.auth.infrastructure.lib.oid.AuthenticationContext
import ru.somarov.auth.infrastructure.lib.oid.AuthenticationRequest
import ru.somarov.auth.infrastructure.lib.oid.JwtService
import ru.somarov.auth.infrastructure.lib.util.generateRandomString
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

        return authCode // Here i should redirect user to login page either in mobile or in web
    }

    // TODO: login function
    // TODO: logout function

    suspend fun token(params: Parameters): TokenResponse {
        val clientId = params["client_id"] ?: throw IllegalArgumentException("Missing client_id")
        val code = params["code"] ?: throw IllegalArgumentException("Missing authorization code")
        val grantType = params["grant_type"] ?: throw IllegalArgumentException("Missing grant_type")
        val codeVerifier = params["code_verifier"] ?: throw IllegalArgumentException("Missing code_verifier")

        if (grantType != "authorization_code") {
            throw IllegalArgumentException("Unsupported grant_type: $grantType")
        }

        val authenticationBytes = keyDbClient.retrieve(Cbor.Default.encodeToByteArray("authentication:$code"))
            ?: throw IllegalArgumentException("Invalid or expired authorization code")

        val authentication = Cbor.Default.decodeFromByteArray<AuthenticationContext>(authenticationBytes)
        if (authentication.request.clientId != clientId) {
            throw IllegalArgumentException("Invalid client_id")
        }

        if (!validate(authentication, codeVerifier)) {
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
