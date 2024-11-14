package ru.somarov.auth.lib.oid

data class AuthenticationContext(
    val userId: String,
    val request: AuthenticationRequest,
)
