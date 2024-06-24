package ru.somarov.auth.presentation.event

import kotlinx.datetime.Instant

data class Metadata(val createdAt: Instant, val key: String, val attempt: Int)
