package dev.juanvega.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Workout(
    val id: String,
    val title: String,
    val description: String? = null,
    val routine_id: String? = null,
    val start_time: Instant,
    val end_time: Instant,
    val updated_at: Instant,
    val created_at: Instant,
    val exercises: List<WorkoutExercise> = emptyList(),
)

@Serializable
data class WorkoutExercise(
    val index: Int,
    val title: String,
    val notes: String? = null,
    val exercise_template_id: String,
    val supersets_id: Int? = null,
    val sets: List<WorkoutSet> = emptyList(),
)

@Serializable
data class WorkoutSet(
    val index: Int,
    val type: String,
    val weight_kg: Double? = null,
    val reps: Int? = null,
    val distance_meters: Int? = null,
    val duration_seconds: Int? = null,
    val rpe: Double? = null,
    val custom_metric: Double? = null,
)
