package ru.somarov.auth.presentation.event

import kotlinx.datetime.Clock.System.now
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Metadata(
    val createdAt: Instant,
    val key: String,
    val attempt: Int
) {
    constructor(key: String): this(now(), key, 0)
}
