package ru.somarov.auth.infrastructure

import kotlinx.serialization.Serializable

@Serializable
data class AuthorizationRequest(val userId: String)
