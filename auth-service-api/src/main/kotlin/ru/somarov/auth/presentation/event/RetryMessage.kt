package ru.somarov.auth.presentation.event

import kotlinx.serialization.Serializable

@Serializable
data class RetryMessage<T>(
    val payload: T,
    val key: String,
    val attempt: Int
)
