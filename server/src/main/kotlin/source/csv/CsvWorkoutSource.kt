package dev.juanvega.source.csv

import dev.juanvega.model.Workout
import dev.juanvega.source.WorkoutSource
import io.ktor.util.logging.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * [WorkoutSource] backed by a Hevy CSV export file.
 *
 * The whole parsed dataset is held as an immutable [Snapshot] swapped atomically on
 * reload — readers never observe a partially loaded state. A failed reload logs and
 * keeps the previous snapshot.
 *
 * Change tracking for `/v1/workouts/events`: each workout's content is hashed.
 * On the first load `updated_at`/`created_at` are taken from the CSV (the workout's
 * end time) so historical `since=` queries work; on later reloads any workout whose
 * content changed gets `updated_at = reload time`. Removed workouts simply vanish
 * (deletions are never emitted).
 */
class CsvWorkoutSource(
    private val dataFile: Path?,
    private val log: Logger,
    private val clock: Clock = Clock.System,
) : WorkoutSource {

    override val isAvailable: Boolean get() = true

    private class TrackedWorkout(
        val workout: Workout,
        val contentHash: Int,
    ) {
        val createdAt: Instant = workout.created_at
        val updatedAt: Instant = workout.updated_at
    }

    private class Snapshot(val workouts: List<TrackedWorkout>) {
        val byId: Map<String, TrackedWorkout> = workouts.associateBy { it.workout.id }
    }

    private val snapshot = AtomicReference(Snapshot(emptyList()))

    /** False until the first successful [loadNow]; the first load keeps CSV timestamps. */
    private var everLoaded = false

    /** Parsed workouts, newest first. */
    val current: List<Workout>
        get() = snapshot.get().workouts.map(TrackedWorkout::workout)

    /**
     * Reads and parses the configured file, merges change-tracking state with the
     * previous snapshot, and swaps it in atomically. On any failure the previous
     * snapshot is kept and the error is logged.
     */
    fun loadNow(): Boolean {
        val path = dataFile
        if (path == null) {
            log.warn("No heavyapp.dataFile configured; serving an empty workout list")
            snapshot.set(Snapshot(emptyList()))
            return false
        }
        val loadedAt = clock.now()
        return runCatching {
            val csvText = Files.newBufferedReader(path).useLines { lines -> lines.joinToString("\n") }
            val parsed = WorkoutCsvReader.parse(csvText)
            val merged = mergeWithPrevious(parsed, previous = snapshot.get(), loadedAt = loadedAt)
            snapshot.set(Snapshot(merged))
            everLoaded = true
            log.info("Loaded {} workout(s) from {}", parsed.size, path)
            true
        }.getOrElse { error ->
            log.error("Failed to load workout CSV from {}; keeping previous snapshot", path, error)
            false
        }
    }

    private fun mergeWithPrevious(
        parsed: List<Workout>,
        previous: Snapshot,
        loadedAt: Instant,
    ): List<TrackedWorkout> = parsed.map { workout ->
        val hash = workout.contentHash()
        val prior = previous.byId[workout.id]
        when {
            // Unchanged since last load: keep original timestamps so clients'
            // `since` cursors stay stable across reloads.
            prior != null && prior.contentHash == hash -> TrackedWorkout(
                workout.copy(created_at = prior.createdAt, updated_at = prior.updatedAt),
                hash,
            )
            // First load ever: trust the CSV (end time) so historical `since=`
            // queries return pre-existing workouts.
            !everLoaded -> TrackedWorkout(workout, hash)
            // Changed or new as of this reload.
            else -> TrackedWorkout(
                workout.copy(
                    created_at = prior?.createdAt ?: workout.created_at,
                    updated_at = loadedAt,
                ),
                hash,
            )
        }
    }

    private fun Workout.contentHash(): Int =
        copy(updated_at = created_at).hashCode()

    override suspend fun workouts(): List<Workout> = current

    override suspend fun workout(id: String): Workout? = current.find { it.id == id }

    override suspend fun workoutEvents(since: Instant): List<Workout> =
        current.filter { it.updated_at >= since }

    /** Outcome of [importCsv]. */
    sealed interface ImportResult {
        /** CSV accepted and written; the background refresh picks it up. */
        data class Success(val workoutCount: Int, val backupPath: Path?) : ImportResult

        /** The uploaded content is not a valid workout CSV; nothing was modified. */
        data class InvalidCsv(val reason: String) : ImportResult

        /** The file could not be written; the previous file is untouched. */
        data class Failure(val cause: Throwable) : ImportResult
    }

    /**
     * Validates the uploaded CSV, backs up the current data file as
     * `<name>-<UTC timestamp>-backup.csv`, and replaces it with the new content.
     *
     * The new data becomes visible with the next periodic refresh — no immediate
     * reload is forced, keeping the refresh loop as the single writer of state.
     */
    fun importCsv(csvText: String): ImportResult {
        val parsed = runCatching { WorkoutCsvReader.parse(csvText) }.getOrElse { error ->
            return ImportResult.InvalidCsv(error.message ?: "invalid CSV")
        }
        val path = dataFile ?: return ImportResult.InvalidCsv("No heavyapp.dataFile configured")

        return runCatching {
            val backup = backupCurrent(path)
            // Write to a temp file first so readers never see a partial CSV.
            val temp = Files.createTempFile(path.toAbsolutePath().parent, "import", ".csv")
            Files.writeString(temp, csvText)
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            log.info("Imported {} workout(s); previous file backed up to {}", parsed.size, backup ?: "(none)")
            ImportResult.Success(workoutCount = parsed.size, backupPath = backup)
        }.getOrElse { error ->
            log.error("Failed to import CSV into {}; keeping previous file", path, error)
            ImportResult.Failure(error)
        }
    }

    private fun backupCurrent(path: Path): Path? {
        if (!Files.exists(path)) return null
        val name = path.fileName.toString()
        val base = name.removeSuffix(".csv")
        val stamp = LocalDateTime.now(ZoneOffset.UTC).format(BACKUP_STAMP)
        val backup = path.resolveSibling("$base-$stamp-backup.csv")
        Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING)
        return backup
    }
}

private val BACKUP_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
