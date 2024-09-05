package ru.somarov.auth.infrastructure.db.entity

import java.util.UUID

data class ClientAuthenticationProviderInfo(
    val id: UUID,
    val authenticationProviderId: UUID,
    val clientId: String
)
