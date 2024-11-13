package ru.somarov.auth.presentation.request

import kotlinx.serialization.Serializable

@Serializable
data class LogoutRequest(
    val accessToken: String,
    val refreshToken: String,
    val idToken: String?
)
