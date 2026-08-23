package dev.juanvega.source.csv

import dev.juanvega.model.Workout
import dev.juanvega.source.WorkoutSource
import io.ktor.util.logging.Logger
import java.nio.file.Files
import java.nio.file.Path
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
}
