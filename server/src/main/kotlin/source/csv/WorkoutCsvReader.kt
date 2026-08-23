package dev.juanvega.source.csv

import dev.juanvega.model.Workout
import dev.juanvega.model.WorkoutExercise
import dev.juanvega.model.WorkoutSet
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.toKotlinInstant

/**
 * Reads Hevy's flat CSV export format (one row per performed set) and groups rows
 * into [Workout]s:
 *
 * ```
 * "title","start_time","end_time","description","exercise_title","superset_id",
 * "exercise_notes","set_index","set_type","weight_kg","reps","distance_km",
 * "duration_seconds","rpe"
 * ```
 */
internal object WorkoutCsvReader {

    /** Column names as they appear in the export header. */
    private enum class Column {
        title, start_time, end_time, description, exercise_title, superset_id,
        exercise_notes, set_index, set_type, weight_kg, reps, distance_km,
        duration_seconds, rpe;
    }

    internal data class Row(
        val title: String,
        val startTime: LocalDateTime,
        val endTime: LocalDateTime?,
        val description: String?,
        val exerciseTitle: String,
        val supersetId: Int?,
        val exerciseNotes: String?,
        val setIndex: Int,
        val setType: String,
        val weightKg: Double?,
        val reps: Int?,
        val distanceKm: Double?,
        val durationSeconds: Int?,
        val rpe: Double?,
    )

    fun parse(csvText: String): List<Workout> = parse(CsvParser.parse(csvText))

    fun parse(records: List<List<String>>): List<Workout> {
        if (records.isEmpty()) return emptyList()

        val header = records.first().map { it.trim() }
        require(Column.entries.all { header.contains(it.name) }) {
            "CSV header is missing columns: ${Column.entries.map { it.name }.filterNot(header::contains)}"
        }
        val indexOf = Column.entries.associateWith { header.indexOf(it.name) }

        fun cells(rowCells: List<String>): ((Column) -> String) = { column ->
            rowCells.getOrNull(indexOf.getValue(column))?.trim().orEmpty()
        }

        return records.drop(1)
            .map { cells(it) }
            .mapNotNull { cell ->
                val startTime = cell(Column.start_time).parseTimestampOrNull()
                    ?: return@mapNotNull null // Unparseable timestamp: skip the row.
                Row(
                    title = cell(Column.title),
                    startTime = startTime,
                    endTime = cell(Column.end_time).takeIf { it.isNotEmpty() }?.parseTimestampOrNull(),
                    description = cell(Column.description).takeIf { it.isNotEmpty() },
                    exerciseTitle = cell(Column.exercise_title),
                    supersetId = cell(Column.superset_id).toIntOrNull(),
                    exerciseNotes = cell(Column.exercise_notes).takeIf { it.isNotEmpty() },
                    setIndex = cell(Column.set_index).toIntOrNull() ?: 0,
                    setType = cell(Column.set_type).ifEmpty { "normal" },
                    weightKg = cell(Column.weight_kg).toDoubleOrNull(),
                    reps = cell(Column.reps).toIntOrNull(),
                    distanceKm = cell(Column.distance_km).toDoubleOrNull(),
                    durationSeconds = cell(Column.duration_seconds).toDoubleOrNull()?.toInt(),
                    rpe = cell(Column.rpe).toDoubleOrNull(),
                )
            }
            .groupToWorkouts()
    }

    /**
     * Rows sharing (title, start_time) belong to the same workout; exercises keep
     * their order of first appearance and sets are ordered by `set_index`.
     */
    private fun List<Row>.groupToWorkouts(): List<Workout> =
        groupBy { it.title to it.startTime }
            .values
            .map { workoutRows ->
                val head = workoutRows.first()
                val exercises = workoutRows
                    .groupBy { it.exerciseTitle }
                    .let { grouped ->
                        // Preserve order of first appearance in the CSV.
                        workoutRows.map { it.exerciseTitle }.distinct().mapNotNull(grouped::get)
                    }
                    .mapIndexed { index, exerciseRows ->
                        val sorted = exerciseRows.sortedBy { it.setIndex }
                        val first = sorted.first()
                        WorkoutExercise(
                            index = index,
                            title = first.exerciseTitle,
                            notes = first.exerciseNotes,
                            exercise_template_id = stableId("exercise-template", first.exerciseTitle),
                            supersets_id = first.supersetId,
                            sets = sorted.mapIndexed { setIndex, row ->
                                WorkoutSet(
                                    index = setIndex,
                                    type = row.setType,
                                    weight_kg = row.weightKg,
                                    reps = row.reps,
                                    distance_meters = row.distanceKm?.let { km -> (km * 1000).toInt() },
                                    duration_seconds = row.durationSeconds,
                                    rpe = row.rpe,
                                )
                            },
                        )
                    }
                val start = head.startTime.toInstant(ZoneOffset.UTC).toKotlinInstant()
                val end = (head.endTime ?: head.startTime).toInstant(ZoneOffset.UTC).toKotlinInstant()
                Workout(
                    id = stableId("workout", head.title, head.startTime.toString()),
                    title = head.title,
                    description = head.description,
                    start_time = start,
                    end_time = end,
                    updated_at = end,
                    created_at = end,
                    exercises = exercises,
                )
            }
            .sortedByDescending { it.start_time }
}

/** `17 Aug 2026, 15:17` — the timestamp format of Hevy's CSV export. */
private val TIMESTAMP_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm", Locale.ENGLISH)

private fun String.parseTimestampOrNull(): LocalDateTime? =
    runCatching { LocalDateTime.parse(this, TIMESTAMP_FORMAT) }.getOrNull()
