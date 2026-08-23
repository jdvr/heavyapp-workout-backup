package dev.juanvega.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Routine(
    val id: String,
    val title: String,
    val folder_id: Int? = null,
    val updated_at: Instant,
    val created_at: Instant,
    val notes: String? = null,
    val exercises: List<RoutineExercise> = emptyList(),
)

@Serializable
data class RoutineExercise(
    val index: Int,
    val title: String,
    val rest_seconds: Int? = null,
    val notes: String? = null,
    val exercise_template_id: String,
    val supersets_id: Int? = null,
    val sets: List<RoutineSet> = emptyList(),
)

@Serializable
data class RoutineSet(
    val index: Int,
    val type: String,
    val weight_kg: Double? = null,
    val reps: Int? = null,
    val rep_range: RepRange? = null,
    val distance_meters: Int? = null,
    val duration_seconds: Int? = null,
    val rpe: Double? = null,
    val custom_metric: Double? = null,
)

@Serializable
data class RepRange(
    val start: Int? = null,
    val end: Int? = null,
)
