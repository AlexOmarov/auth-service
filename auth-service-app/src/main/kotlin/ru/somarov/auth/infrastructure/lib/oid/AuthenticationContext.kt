package ru.somarov.auth.infrastructure.lib.oid

data class AuthenticationContext(
    val userId: String,
    val request: AuthenticationRequest,
)
