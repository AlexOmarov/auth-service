package ru.somarov.auth.presentation.event.broadcast

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import ru.somarov.auth.serialization.UUIDSerializer
import java.util.UUID

@Serializable
data class RegistrationBroadcast(@Serializable(with = UUIDSerializer::class) val id: UUID, val time: Instant)
