package ru.somarov.auth.infrastructure.db.entity

data class ClientAuthenticationProviderInfo(
    val id: String,
    val authenticationProviderId: String,
    val clientId: String
)
