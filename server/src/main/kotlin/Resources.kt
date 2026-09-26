package dev.juanvega

import io.ktor.resources.Resource
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Type-safe route resources mirroring the Hevy public API paths.
 * No `api-key` header, no pagination — this server is a single-user mirror.
 */
object Resources {

    @Serializable
    @Resource("/v1/workouts")
    class Workouts

    @Serializable
    @Resource("/v1/workouts/count")
    class WorkoutCount

    @Serializable
    @Resource("/v1/workouts/events")
    class WorkoutEvents(val since: Instant? = null)

    /**
     * Custom endpoint (not part of the Hevy API): imports a CSV export into the CSV
     * source file. The body is the raw CSV (`text/csv`); the previous file is backed up
     * first. `mode` is `merge` (default: keep stored workouts the upload omits) or
     * `replace` (drop everything the upload does not contain).
     */
    @Serializable
    @Resource("/v1/workouts/import")
    class WorkoutImport(val mode: String? = null)

    @Serializable
    @Resource("/v1/workouts/{id}")
    class WorkoutById(val id: String)

    @Serializable
    @Resource("/v1/routines")
    class Routines

    @Serializable
    @Resource("/v1/routines/{id}")
    class RoutineById(val id: String)

    @Serializable
    @Resource("/v1/exercise_templates")
    class ExerciseTemplates

    @Serializable
    @Resource("/v1/exercise_templates/{id}")
    class ExerciseTemplateById(val id: String)

    @Serializable
    @Resource("/v1/routine_folders")
    class RoutineFolders

    @Serializable
    @Resource("/v1/routine_folders/{id}")
    class RoutineFolderById(val id: Int)

    @Serializable
    @Resource("/v1/exercise_history/{exerciseTemplateId}")
    class ExerciseHistory(
        val exerciseTemplateId: String,
        val start_date: Instant? = null,
        val end_date: Instant? = null,
    )

    @Serializable
    @Resource("/v1/body_measurements")
    class BodyMeasurements

    @Serializable
    @Resource("/v1/body_measurements/{date}")
    class BodyMeasurementByDate(val date: LocalDate)

    @Serializable
    @Resource("/v1/user/info")
    class UserInfo
}
