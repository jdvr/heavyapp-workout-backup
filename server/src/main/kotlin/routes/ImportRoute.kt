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
    val added: Int,
    val updated: Int,
    val unchanged: Int,
    val preserved: Int,
    val backup: String? = null,
)

/**
 * `POST /v1/workouts/import` — custom endpoint: writes the uploaded CSV into the CSV
 * source file. The request body is the raw CSV; the current file is backed up as
 * `<name>-<timestamp>-backup.csv` before being overwritten, and the new data is
 * served after the next periodic refresh.
 *
 * `?mode=merge` (the default) keeps the workouts already stored that the upload does
 * not mention — Hevy's export only covers a rolling window, so a plain re-import must
 * not wipe older history. `?mode=replace` drops them and keeps only the upload.
 *
 * Responds `503` while no CSV source is configured at all, `400` for an unknown mode.
 */
fun Route.csvImportRoute(csvSource: CsvWorkoutSource?) {
    post<Resources.WorkoutImport> { resource ->
        call.handleCsvImport(csvSource, resource.mode)
    }.withSkillDescription(
        summary = "Import CSV",
        description = "Custom (not in Hevy API): replaces the CSV source. Request body is raw `text/csv`; `Content-Type: text/csv`. Previous file backed up as `<name>-<timestamp>-backup.csv`. New data visible after next refresh (`heavyapp.refreshSeconds`, default 60s). `200 { status, workouts, backup }`, `400` on blank/invalid CSV, `503` if no CSV source configured.",
    )
}

private suspend fun ApplicationCall.handleCsvImport(csvSource: CsvWorkoutSource?, requestedMode: String?) {
    if (csvSource == null) {
        unavailable("CSV source")
        return
    }
    val mode = requestedMode.toImportMode()
    if (mode == null) {
        respond(
            HttpStatusCode.BadRequest,
            ErrorResponse("Unknown mode '$requestedMode'; expected 'merge' (default) or 'replace'"),
        )
        return
    }
    val csvText = receiveText()
    if (csvText.isBlank()) {
        respond(HttpStatusCode.BadRequest, ErrorResponse("Request body must contain CSV data"))
        return
    }
    when (val result = csvSource.importCsv(csvText, mode)) {
        is CsvWorkoutSource.ImportResult.Success -> respond(
            HttpStatusCode.OK,
            ImportResponse(
                status = "imported",
                workouts = result.workouts,
                added = result.added,
                updated = result.updated,
                unchanged = result.unchanged,
                preserved = result.preserved,
                backup = result.backupPath?.toString(),
            ),
        )
        is CsvWorkoutSource.ImportResult.InvalidCsv ->
            respond(HttpStatusCode.BadRequest, ErrorResponse(result.reason))
        is CsvWorkoutSource.ImportResult.Failure ->
            respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to write CSV file"))
    }
}

private fun String?.toImportMode(): CsvWorkoutSource.ImportMode? =
    when (this?.trim()?.lowercase()) {
        null, "" -> CsvWorkoutSource.ImportMode.Merge
        "merge" -> CsvWorkoutSource.ImportMode.Merge
        "replace" -> CsvWorkoutSource.ImportMode.Replace
        else -> null
    }
