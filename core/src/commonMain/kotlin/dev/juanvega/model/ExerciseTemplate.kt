package dev.juanvega.model

import kotlinx.serialization.Serializable

@Serializable
data class ExerciseTemplate(
    val id: String,
    val title: String,
    val type: CustomExerciseType,
    val primary_muscle_group: MuscleGroup,
    val secondary_muscle_groups: List<MuscleGroup> = emptyList(),
    val equipment_category: EquipmentCategory,
    val is_custom: Boolean = false,
)

@Serializable
enum class CustomExerciseType {
    weight_reps,
    reps_only,
    bodyweight_reps,
    bodyweight_assisted_reps,
    duration,
    weight_duration,
    distance_duration,
    short_distance_weight,
}

@Serializable
enum class MuscleGroup {
    abdominals, shoulders, biceps, triceps, forearms, quadriceps, hamstrings, calves,
    glutes, abductors, adductors, lats, upper_back, traps, lower_back, chest,
    cardio, neck, full_body, other,
}

@Serializable
enum class EquipmentCategory {
    none, barbell, dumbbell, kettlebell, machine, plate, resistance_band, suspension, other,
}
