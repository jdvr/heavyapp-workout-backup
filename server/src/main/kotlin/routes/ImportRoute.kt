package dev.juanvega.routes

import dev.juanvega.Resources
import dev.juanvega.source.csv.CsvWorkoutSource
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable

@Serializable
internal data class ImportResponse(
    val status: String,
    val workouts: Int,
    val backup: String? = null,
)

/**
 * `POST /v1/workouts/import` — custom endpoint: replaces the CSV source file.
 * The request body is the raw CSV. The current file is backed up as
 * `<name>-<timestamp>-backup.csv` before being overwritten; the new data is
 * served after the next periodic refresh.
 *
 * Responds `503` while no CSV source is configured at all.
 */
fun Route.csvImportRoute(csvSource: CsvWorkoutSource?) {
    post<Resources.WorkoutImport> {
        call.handleCsvImport(csvSource)
    }.withSkillDescription(
        summary = "Import CSV",
        description = "Custom (not in Hevy API): replaces the CSV source. Request body is raw `text/csv`; `Content-Type: text/csv`. Previous file backed up as `<name>-<timestamp>-backup.csv`. New data visible after next refresh (`heavyapp.refreshSeconds`, default 60s). `200 { status, workouts, backup }`, `400` on blank/invalid CSV, `503` if no CSV source configured.",
    )
}

private suspend fun ApplicationCall.handleCsvImport(csvSource: CsvWorkoutSource?) {
    if (csvSource == null) {
        unavailable("CSV source")
        return
    }
    val csvText = receiveText()
    if (csvText.isBlank()) {
        respond(HttpStatusCode.BadRequest, ErrorResponse("Request body must contain CSV data"))
        return
    }
    when (val result = csvSource.importCsv(csvText)) {
        is CsvWorkoutSource.ImportResult.Success -> respond(
            HttpStatusCode.OK,
            ImportResponse(status = "imported", workouts = result.workoutCount, backup = result.backupPath?.toString()),
        )
        is CsvWorkoutSource.ImportResult.InvalidCsv ->
            respond(HttpStatusCode.BadRequest, ErrorResponse(result.reason))
        is CsvWorkoutSource.ImportResult.Failure ->
            respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to write CSV file"))
    }
}
