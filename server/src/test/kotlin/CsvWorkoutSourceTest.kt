package dev.juanvega

import dev.juanvega.source.csv.CsvWorkoutSource
import io.ktor.util.logging.KtorSimpleLogger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.time.Instant

private class FakeClock(private var current: Instant) : Clock {
    override fun now(): Instant = current
    fun advanceTo(instant: Instant) { current = instant }
}

class CsvWorkoutSourceTest {

    private val log = KtorSimpleLogger("test")

    private fun tempCsv(content: String): Path =
        Files.createTempFile("workouts", ".csv").apply { writeText(content) }

    private val csvV1 = """
        "title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"
        "Leg Day","17 Aug 2026, 15:17","17 Aug 2026, 15:53","","Bench Press (Barbell)",,"",0,"normal",20,10,,,
    """.trimIndent()

    @Test
    fun `first load stamps created and updated with the workout end time`() {
        val file = tempCsv(csvV1)
        val source = CsvWorkoutSource(file, log)
        assertTrue(source.loadNow())

        val workout = runBlocking { source.workouts() }.single()
        assertEquals("2026-08-17T15:53:00Z", workout.updated_at.toString())
        assertEquals(workout.updated_at, workout.created_at)
        Files.deleteIfExists(file)
    }

    @Test
    fun `reload keeps timestamps for unchanged workouts`() {
        val file = tempCsv(csvV1)
        val reloadTime = Instant.parse("2026-08-22T00:00:00Z")
        val clock = FakeClock(Instant.DISTANT_PAST)
        val source = CsvWorkoutSource(file, log, clock)

        source.loadNow()
        clock.advanceTo(reloadTime)
        assertTrue(source.loadNow())

        val workout = runBlocking { source.workouts() }.single()
        assertEquals("2026-08-17T15:53:00Z", workout.updated_at.toString())
        Files.deleteIfExists(file)
    }

    @Test
    fun `changed workout gets updated_at of reload time but keeps created_at`() {
        val file = tempCsv(csvV1)
        val reloadTime = Instant.parse("2026-08-22T00:00:00Z")
        val clock = FakeClock(Instant.DISTANT_PAST)
        val source = CsvWorkoutSource(file, log, clock)
        source.loadNow()
        val originalId = runBlocking { source.workouts() }.single().id

        file.writeText(
            csvV1.replace("\"normal\",20,10", "\"normal\",24,10"),
        )
        clock.advanceTo(reloadTime)
        source.loadNow()

        val workout = runBlocking { source.workouts() }.single()
        assertEquals(originalId, workout.id)
        assertEquals(reloadTime, workout.updated_at)
        assertEquals("2026-08-17T15:53:00Z", workout.created_at.toString())
        Files.deleteIfExists(file)
    }

    @Test
    fun `workoutEvents filters by since newest-first`() {
        val file = tempCsv(
            """
            "title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"
            "Old","1 Jan 2026, 09:00","1 Jan 2026, 09:30","","Bench Press (Barbell)",,"",0,"normal",20,10,,,
            "New","20 Aug 2026, 09:00","20 Aug 2026, 09:30","","Plank",,"",0,"normal",,,,30,
            """.trimIndent(),
        )
        val source = CsvWorkoutSource(file, log)
        source.loadNow()

        val all = runBlocking { source.workoutEvents(Instant.parse("2025-06-01T00:00:00Z")) }
        assertEquals(listOf("New", "Old"), all.map { it.title }) // Newest first.

        val onlyNew = runBlocking { source.workoutEvents(Instant.parse("2026-08-15T00:00:00Z")) }
        assertEquals(listOf("New"), onlyNew.map { it.title })

        val none = runBlocking { source.workoutEvents(Instant.parse("2027-01-01T00:00:00Z")) }
        assertTrue(none.isEmpty())
        Files.deleteIfExists(file)
    }

    @Test
    fun `failed load keeps previous snapshot`() {
        val file = tempCsv(csvV1)
        val source = CsvWorkoutSource(file, log)
        source.loadNow()

        file.writeText("this is not,csv\nwith,broken \"quotes")
        assertFalse(source.loadNow())
        assertEquals(1, source.current.size)
        Files.deleteIfExists(file)
    }

    @Test
    fun `missing dataFile yields an available-but-empty source`() {
        val source = CsvWorkoutSource(null, log)
        assertTrue(source.isAvailable)
        assertFalse(source.loadNow())
        assertTrue(source.current.isEmpty())
    }
}
