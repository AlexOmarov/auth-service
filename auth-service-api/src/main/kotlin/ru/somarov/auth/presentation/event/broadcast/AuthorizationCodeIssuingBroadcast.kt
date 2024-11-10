package ru.somarov.auth.presentation.event.broadcast

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class AuthorizationCodeIssuingBroadcast(val id: String, val time: Instant)
