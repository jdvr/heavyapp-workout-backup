package dev.juanvega.source

import dev.juanvega.model.Workout
import kotlinx.datetime.Instant

/**
 * Thrown by [UnavailableSource] implementations; mapped to
 * `503 Service Unavailable` by `StatusPages`.
 */
class SourceUnavailableException(domain: String) :
    RuntimeException("$domain source is not available yet")

/**
 * Placeholder implementation for API domains whose data sources will be added in
 * a later feature (routines, exercise templates, measurements, …). Every call
 * fails with [SourceUnavailableException] so routes can respond `503` uniformly.
 */
object UnavailableSources {

    fun workouts(): WorkoutSource = object : WorkoutSource {
        override val isAvailable = false
        override suspend fun workouts() = throw SourceUnavailableException("workout")
        override suspend fun workout(id: String) = throw SourceUnavailableException("workout")
        override suspend fun workoutEvents(since: Instant) = throw SourceUnavailableException("workout")
    }

    fun exerciseTemplates(): ExerciseTemplateSource = object : ExerciseTemplateSource {
        override val isAvailable = false
        override suspend fun exerciseTemplates() = throw SourceUnavailableException("exercise template")
        override suspend fun exerciseTemplate(id: String) = throw SourceUnavailableException("exercise template")
    }

    fun routines(): RoutineSource = object : RoutineSource {
        override val isAvailable = false
        override suspend fun routines() = throw SourceUnavailableException("routine")
        override suspend fun routine(id: String) = throw SourceUnavailableException("routine")
    }

    fun routineFolders(): RoutineFolderSource = object : RoutineFolderSource {
        override val isAvailable = false
        override suspend fun routineFolders() = throw SourceUnavailableException("routine folder")
        override suspend fun routineFolder(id: Int) = throw SourceUnavailableException("routine folder")
    }

    fun bodyMeasurements(): BodyMeasurementSource = object : BodyMeasurementSource {
        override val isAvailable = false
        override suspend fun bodyMeasurements() = throw SourceUnavailableException("body measurement")
    }

    fun userInfo(): UserInfoSource = object : UserInfoSource {
        override val isAvailable = false
        override suspend fun userInfo() = throw SourceUnavailableException("user info")
    }
}
