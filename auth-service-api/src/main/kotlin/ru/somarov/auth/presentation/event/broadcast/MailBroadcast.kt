package ru.somarov.auth.presentation.event.broadcast

import kotlinx.serialization.Serializable
import ru.somarov.auth.serialization.UUIDSerializer
import java.util.UUID

@Serializable
data class MailBroadcast(@Serializable(with = UUIDSerializer::class) val id: UUID, val status: String)
