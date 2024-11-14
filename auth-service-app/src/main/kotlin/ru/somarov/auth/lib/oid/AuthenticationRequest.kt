package ru.somarov.auth.lib.oid

import io.ktor.http.Parameters

data class AuthenticationRequest(
    val clientId: String,
    val redirectUri: String,
    val responseType: String,
    val scopes: List<String>,
    val state: Map<String, String>,
    val codeChallenge: String,
    val codeChallengeMethod: CodeChallengeMethod,
) {
    enum class CodeChallengeMethod {
        S256, PLAIN
    }

    companion object {
        fun create(params: Parameters): AuthenticationRequest {
            val clientId = getValueOrThrow(params, "client_id")
            val redirectUri = getValueOrThrow(params, "redirect_uri")
            val responseType = getValueOrThrow(params, "response_type")
            val scopes = getValueOrThrow(params, "scopes")
            val state = getValueOrThrow(params, "state")
            val codeChallenge = getValueOrThrow(params, "code_challenge")
            val codeChallengeMethod = getValueOrThrow(params, "code_challenge_method")

            return AuthenticationRequest(
                clientId = clientId,
                redirectUri = redirectUri,
                responseType = responseType,
                scopes = scopes.split(","),
                state = state.split(",").associate { it.split("=").run { it[0].toString() to it[1].toString() } },
                codeChallenge = codeChallenge,
                codeChallengeMethod = CodeChallengeMethod.valueOf(codeChallengeMethod),
            )
        }

        private fun getValueOrThrow(params: Parameters, key: String): String {
            return params[key] ?: throw OidValidationException("Got error while parsing auth request")
        }
    }
}
