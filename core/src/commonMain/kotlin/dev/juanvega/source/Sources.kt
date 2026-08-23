package dev.juanvega.source

import dev.juanvega.model.BodyMeasurement
import dev.juanvega.model.ExerciseTemplate
import dev.juanvega.model.Routine
import dev.juanvega.model.RoutineFolder
import dev.juanvega.model.UserInfo
import dev.juanvega.model.Workout
import kotlinx.datetime.Instant

/**
 * A provider of data for one API domain. Routes check [isAvailable] and respond
 * `503 Service Unavailable` when a domain has no source wired yet.
 */
interface DataSource {
    val isAvailable: Boolean
}

interface WorkoutSource : DataSource {
    /** All workouts, newest first. */
    suspend fun workouts(): List<Workout>

    suspend fun workout(id: String): Workout?

    suspend fun workoutCount(): Int = workouts().size

    /**
     * Workouts updated (or created) at or after [since], newest first.
     * Deletions are never reported.
     */
    suspend fun workoutEvents(since: Instant): List<Workout> =
        workouts().filter { it.updated_at >= since }
}

interface ExerciseTemplateSource : DataSource {
    suspend fun exerciseTemplates(): List<ExerciseTemplate>

    suspend fun exerciseTemplate(id: String): ExerciseTemplate?
}

interface RoutineSource : DataSource {
    suspend fun routines(): List<Routine>

    suspend fun routine(id: String): Routine?
}

interface RoutineFolderSource : DataSource {
    suspend fun routineFolders(): List<RoutineFolder>

    suspend fun routineFolder(id: Int): RoutineFolder?
}

interface BodyMeasurementSource : DataSource {
    suspend fun bodyMeasurements(): List<BodyMeasurement>
}

interface UserInfoSource : DataSource {
    suspend fun userInfo(): UserInfo
}
