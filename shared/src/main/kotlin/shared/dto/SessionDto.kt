package shared.dto

import kotlinx.serialization.Serializable

@Serializable
data class SessionDto(
    val token: String,
)