package dev.juanvega.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class RoutineFolder(
    val id: Int,
    val index: Int,
    val title: String,
    val updated_at: Instant,
    val created_at: Instant,
)
