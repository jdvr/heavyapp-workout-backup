package dev.juanvega.model

import kotlinx.serialization.Serializable

enum class WorkoutEventType {
    updated,
}

/**
 * A workout change event. This server never emits [WorkoutEventType.updated]'s sibling
 * `deleted`: the CSV snapshot source carries no delete journal.
 */
@Serializable
data class WorkoutEvent(
    val type: WorkoutEventType,
    val workout: Workout,
)
