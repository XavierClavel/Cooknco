package com.xavierclavel.cooknco.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SessionDto(val token: String)

@Serializable
data class UserInfo(
    val id: Long,
    val version: Long,
    val username: String,
    val role: String = "USER",
    val joinDate: Long,
    val bio: String,
    val recipesCount: Int,
    val likesCount: Int,
    val cookbooksCount: Int,
    val followersCount: Int,
    val followsCount: Int,
)

@Serializable
data class UserDTO(
    val username: String,
    val password: String? = null,
    val mail: String,
    val bio: String = "",
)
