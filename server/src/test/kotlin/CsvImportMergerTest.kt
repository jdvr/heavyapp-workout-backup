package dev.juanvega

import dev.juanvega.source.csv.CsvImportMerger
import dev.juanvega.source.csv.CsvParser
import dev.juanvega.source.csv.CsvWriter
import kotlin.test.*

private val COLUMNS = listOf(
    "title", "start_time", "end_time", "description", "exercise_title", "superset_id",
    "exercise_notes", "set_index", "set_type", "weight_kg", "reps", "distance_km",
    "duration_seconds", "rpe",
)

private fun header(columns: List<String> = COLUMNS): String =
    columns.joinToString(",") { "\"$it\"" }

private fun row(vararg cells: Pair<String, String>, columns: List<String> = COLUMNS): String {
    val byColumn = cells.toMap()
    return columns.joinToString(",") { "\"${byColumn[it].orEmpty()}\"" }
}

/** One CSV row (one performed set) of the workout titled [title]. */
private fun setRow(
    title: String,
    start: String,
    weightKg: Int,
    setIndex: Int = 0,
    columns: List<String> = COLUMNS,
): String = row(
    "title" to title,
    "start_time" to start,
    "end_time" to start,
    "exercise_title" to "Bench Press (Barbell)",
    "set_index" to setIndex.toString(),
    "set_type" to "normal",
    "weight_kg" to weightKg.toString(),
    "reps" to "10",
    columns = columns,
)

private fun csv(vararg rows: String): String = (listOf(header()) + rows).joinToString("\n")

private fun parsed(csvText: String): List<List<String>> = CsvParser.parse(csvText)

class CsvImportMergerTest {

    private val oldWorkout = setRow("Old", "1 Jan 2026, 09:00", weightKg = 20)
    private val freshWorkout = setRow("Fresh", "23 Aug 2026, 10:00", weightKg = 40)

    @Test
    fun `stored workouts the upload does not mention are preserved`() {
        val stored = parsed(csv(oldWorkout, setRow("Spring", "1 Apr 2026, 09:00", weightKg = 30)))
        val uploaded = parsed(csv(freshWorkout))

        val outcome = CsvImportMerger.merge(stored, uploaded)

        assertEquals(1, outcome.added)
        assertEquals(0, outcome.updated)
        assertEquals(0, outcome.unchanged)
        assertEquals(2, outcome.preserved)
        assertEquals(3, outcome.workoutCount)

        val merged = parsed(CsvWriter.write(outcome.records))
        assertEquals(COLUMNS, merged.first())
        assertEquals(
            listOf("Fresh", "Old", "Spring"),
            merged.drop(1).map { it[0] },
        )
    }

    @Test
    fun `the upload wins for a workout it repeats, replacing all its rows`() {
        // Same workout (title + start_time) exported again with one set instead of two.
        val stored = parsed(csv(setRow("Leg Day", "17 Aug 2026, 15:17", weightKg = 20, setIndex = 0), setRow("Leg Day", "17 Aug 2026, 15:17", weightKg = 20, setIndex = 1)))
        val uploaded = parsed(csv(setRow("Leg Day", "17 Aug 2026, 15:17", weightKg = 30)))

        val outcome = CsvImportMerger.merge(stored, uploaded)

        assertEquals(0, outcome.added)
        assertEquals(1, outcome.updated)
        assertEquals(1, outcome.workoutCount)

        val merged = parsed(CsvWriter.write(outcome.records)).drop(1)
        assertEquals(1, merged.size, "the replaced workout must not leave orphan sets behind")
        assertEquals("30", merged.single()[COLUMNS.indexOf("weight_kg")])
    }

    @Test
    fun `re-importing the same export is a no-op`() {
        val stored = csv(oldWorkout, freshWorkout)
        val records = parsed(stored)

        val outcome = CsvImportMerger.merge(records, records)

        assertEquals(0, outcome.added)
        assertEquals(0, outcome.updated)
        assertEquals(2, outcome.unchanged)
        assertEquals(0, outcome.preserved)
        assertEquals(stored, CsvWriter.write(outcome.records))
    }

    @Test
    fun `a fresh import keeps working when there is nothing stored yet`() {
        val uploaded = parsed(csv(freshWorkout))

        val outcome = CsvImportMerger.merge(storedRecords = null, uploadedRecords = uploaded)

        assertEquals(1, outcome.added)
        assertEquals(1, outcome.workoutCount)
        assertEquals(csv(freshWorkout), CsvWriter.write(outcome.records))
    }

    @Test
    fun `rows that belong to no workout are kept verbatim`() {
        val junk = "\"\",\"not a date\",\"\",\"\",\"Plank\",,\"\",0,\"normal\",,,,30,"
        val stored = parsed(csv(oldWorkout, junk))
        val uploaded = parsed(csv(freshWorkout))

        val outcome = CsvImportMerger.merge(stored, uploaded)

        assertEquals(1, outcome.preserved)
        val merged = parsed(CsvWriter.write(outcome.records))
        assertEquals(listOf("Fresh", "Old", ""), merged.drop(1).map { it[0] })
    }

    @Test
    fun `stored rows are realigned when the export column order changed`() {
        val shuffled = COLUMNS.reversed()
        val stored = parsed(
            (listOf(header(shuffled)) + setRow("Old", "1 Jan 2026, 09:00", weightKg = 20, columns = shuffled))
                .joinToString("\n"),
        )
        val uploaded = parsed(csv(freshWorkout))

        val outcome = CsvImportMerger.merge(stored, uploaded)

        assertEquals(1, outcome.preserved)
        val preserved = parsed(CsvWriter.write(outcome.records)).drop(1).single { it[0] == "Old" }
        // Values must land under their own column, not wherever the old order put them.
        assertEquals("20", preserved[COLUMNS.indexOf("weight_kg")])
        assertEquals("Bench Press (Barbell)", preserved[COLUMNS.indexOf("exercise_title")])
        assertEquals("1 Jan 2026, 09:00", preserved[COLUMNS.indexOf("start_time")])
    }
}
