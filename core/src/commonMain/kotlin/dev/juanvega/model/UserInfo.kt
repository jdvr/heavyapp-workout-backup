package dev.juanvega.model

import kotlinx.serialization.Serializable

@Serializable
data class UserInfo(
    val id: String,
    val name: String,
    val url: String,
)

@Serializable
data class UserInfoResponse(
    val data: UserInfo,
)
