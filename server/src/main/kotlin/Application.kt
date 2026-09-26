package dev.juanvega

import dev.juanvega.config.AppSettings
import dev.juanvega.routes.csvImportRoute
import dev.juanvega.routes.metaRoutes
import dev.juanvega.routes.skillRoute
import dev.juanvega.routes.workoutRoutes
import dev.juanvega.source.BodyMeasurementSource
import dev.juanvega.source.ExerciseTemplateSource
import dev.juanvega.source.RoutineFolderSource
import dev.juanvega.source.RoutineSource
import dev.juanvega.source.UnavailableSources
import dev.juanvega.source.UserInfoSource
import dev.juanvega.source.WorkoutSource
import dev.juanvega.source.csv.CsvWorkoutSource
import dev.juanvega.source.csv.launchCsvRefresh
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.resources.Resources as ResourcesPlugin
import io.ktor.server.routing.*
import io.opentelemetry.api.OpenTelemetry
import java.nio.file.Path
import kotlinx.serialization.json.Json

/** Shared JSON configuration — identical on client and server per AGENT.md. */
val ApiJson: Json = Json {
    encodeDefaults = false
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun Application.rootModule() {
    val openTelemetry = getOpenTelemetry(serviceName = "heavyapp-workout-backup")

    configureOpenTelemetry(openTelemetry)
    configureStatusPages()
    configureHttp()

    val settings = AppSettings.from(environment.config)
    val csvWorkouts = CsvWorkoutSource(
        dataFile = settings.dataFile?.let(Path::of),
        log = log,
    )
    launchCsvRefresh(csvWorkouts, settings, openTelemetry.getTracer("dev.juanvega.csv"))

    apiModule(
        workouts = csvWorkouts,
        csvSource = csvWorkouts,
        exerciseTemplates = UnavailableSources.exerciseTemplates(),
        routineFolders = UnavailableSources.routineFolders(),
        routines = UnavailableSources.routines(),
        bodyMeasurements = UnavailableSources.bodyMeasurements(),
        userInfo = UnavailableSources.userInfo(),
    )
}

/** Wires all `/v1` routes to their data sources; injectable for tests. */
fun Application.apiModule(
    workouts: WorkoutSource,
    csvSource: CsvWorkoutSource? = null,
    exerciseTemplates: ExerciseTemplateSource,
    routineFolders: RoutineFolderSource,
    routines: RoutineSource,
    bodyMeasurements: BodyMeasurementSource,
    userInfo: UserInfoSource,
) {
    install(ContentNegotiation) { json(ApiJson) }
    install(ResourcesPlugin)
    routing {
        // Skill endpoint first so it can introspect the rest of the tree on demand
        skillRoute(workouts, exerciseTemplates, routineFolders, routines, bodyMeasurements, userInfo)
        workoutRoutes(workouts)
        csvImportRoute(csvSource)
        metaRoutes(exerciseTemplates, routineFolders, routines, bodyMeasurements, userInfo)
    }
}
