package ru.somarov.auth.infrastructure.lib.oid

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
            val clientId = params["client_id"]!!
            val redirectUri = params["redirect_uri"]!!
            val responseType = params["response_type"]!!
            val scopes = params["scopes"]!!
            val state = params["state"]!!
            val codeChallenge = params["code_challenge"]!!
            val codeChallengeMethod = params["code_challenge_method"]!!

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
    }
}
