package ru.somarov.auth.infrastructure.lib.oid

import io.ktor.http.Parameters

data class TokenRequest(
    val clientId: String,
    val code: String,
    val grantType: String,
    val codeVerifier: String
) {
    companion object {
        fun create(params: Parameters): TokenRequest {
            val clientId = getValueOrThrow(params, "client_id")
            val code = getValueOrThrow(params, "code")
            val grantType = getValueOrThrow(params, "grant_type")
            val codeVerifier = getValueOrThrow(params, "code_verifier")

            if(code != "authorization_code") {
                throw OidValidationException("Got error while parsing auth request")
            }

            return TokenRequest(
                clientId = clientId,
                code = code,
                grantType = grantType,
                codeVerifier = codeVerifier
            )
        }

        private fun getValueOrThrow(params: Parameters, key: String): String {
            return params[key] ?: throw OidValidationException("Got error while parsing auth request")
        }
    }
}
