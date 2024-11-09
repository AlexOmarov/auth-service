package ru.somarov.auth.presentation.request

import kotlinx.serialization.Serializable

@Serializable
data class TokenRequest(val grantType: String, val clientId: String, val clientSecret: String)
