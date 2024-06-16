package ru.somarov.auth.presentation.request

import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.serialization.Serializable

@Serializable
@Schema(description = "Object which holds details of authorization")
data class Authorization(val accessToken: String, val refreshToken: String)
