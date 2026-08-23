package dev.juanvega.routes

import dev.juanvega.Resources
import dev.juanvega.model.Workout
import dev.juanvega.model.WorkoutEvent
import dev.juanvega.model.WorkoutEventType
import dev.juanvega.source.WorkoutSource
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
internal data class WorkoutsResponse(val workouts: List<Workout>)

@Serializable
internal data class WorkoutCountResponse(val workout_count: Int)

@Serializable
internal data class WorkoutEventsResponse(val events: List<WorkoutEvent>)

@Serializable
internal data class ExerciseHistoryEntry(
    val workout_id: String,
    val workout_title: String,
    val workout_start_time: Instant,
    val workout_end_time: Instant,
    val exercise_template_id: String,
    val weight_kg: Double? = null,
    val reps: Int? = null,
    val distance_meters: Int? = null,
    val duration_seconds: Int? = null,
    val rpe: Double? = null,
    val custom_metric: Double? = null,
    val set_type: String? = null,
)

@Serializable
internal data class ExerciseHistoryResponse(val exercise_history: List<ExerciseHistoryEntry>)

fun Route.workoutRoutes(workouts: WorkoutSource) {
    get<Resources.Workouts> {
        call.respond(WorkoutsResponse(workouts.workouts()))
    }

    get<Resources.WorkoutCount> {
        call.respond(WorkoutCountResponse(workout_count = workouts.workoutCount()))
    }

    get<Resources.WorkoutEvents> { resource ->
        // Deletions are never emitted; every event is an "updated" event.
        val events = workouts.workoutEvents(resource.since ?: Instant.DISTANT_PAST)
            .map { WorkoutEvent(WorkoutEventType.updated, it) }
        call.respond(WorkoutEventsResponse(events))
    }

    get<Resources.WorkoutById> { resource ->
        val workout = workouts.workout(resource.id)
        if (workout == null) {
            call.notFound("Workout ${resource.id} not found")
            return@get
        }
        call.respond(workout)
    }

    // Derivable today thanks to deterministic template IDs; will switch to the
    // dedicated exercise-template source once that domain is wired.
    get<Resources.ExerciseHistory> { resource ->
        val entries = workouts.workouts()
            .asSequence()
            .flatMap { workout ->
                workout.exercises.asSequence()
                    .filter { it.exercise_template_id == resource.exerciseTemplateId }
                    .flatMap { exercise ->
                        exercise.sets.map { set ->
                            ExerciseHistoryEntry(
                                workout_id = workout.id,
                                workout_title = workout.title,
                                workout_start_time = workout.start_time,
                                workout_end_time = workout.end_time,
                                exercise_template_id = exercise.exercise_template_id,
                                weight_kg = set.weight_kg,
                                reps = set.reps,
                                distance_meters = set.distance_meters,
                                duration_seconds = set.duration_seconds,
                                rpe = set.rpe,
                                custom_metric = set.custom_metric,
                                set_type = set.type,
                            )
                        }
                    }
            }
            .filter { entry -> resource.start_date?.let { entry.workout_start_time >= it } ?: true }
            .filter { entry -> resource.end_date?.let { entry.workout_start_time <= it } ?: true }
            .sortedByDescending { it.workout_start_time }
            .toList()
        call.respond(ExerciseHistoryResponse(entries))
    }
}
