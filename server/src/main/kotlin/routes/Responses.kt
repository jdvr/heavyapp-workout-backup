package dev.juanvega.routes

import dev.juanvega.model.BodyMeasurement
import dev.juanvega.model.ExerciseTemplate
import dev.juanvega.model.Routine
import dev.juanvega.model.RoutineFolder
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

// Shared response envelopes (Hevy field names, no pagination fields).
@Serializable
internal data class ErrorResponse(val error: String)

@Serializable
internal data class ExerciseTemplatesResponse(val exercise_templates: List<ExerciseTemplate>)

@Serializable
internal data class RoutineFoldersResponse(val routine_folders: List<RoutineFolder>)

@Serializable
internal data class RoutinesResponse(val routines: List<Routine>)

@Serializable
internal data class SingleRoutineResponse(val routine: Routine)

@Serializable
internal data class BodyMeasurementsResponse(val body_measurements: List<BodyMeasurement>)

/** `404` with the error shape the Hevy API uses. */
internal suspend fun ApplicationCall.notFound(message: String) {
    respond(HttpStatusCode.NotFound, ErrorResponse(message))
}

/** `503` while a domain's data source is not wired yet. */
internal suspend fun ApplicationCall.unavailable(domain: String) {
    respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("$domain source is not available yet"))
}
