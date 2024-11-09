package ru.somarov.auth.presentation.request

import kotlinx.serialization.Serializable

@Serializable
data class AuthorizationRequest(
    val responseType: String,
    val clientId: String,
    val redirectUri: String,
    val scope: String,
    val state: String,
    val codeChallenge: String
)
