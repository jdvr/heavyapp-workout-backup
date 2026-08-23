package dev.juanvega

import dev.juanvega.source.csv.WorkoutCsvReader
import kotlin.test.*

class WorkoutCsvReaderTest {

    private val csv = """
        "title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"
        "Leg Day 🔥","17 Aug 2026, 15:17","17 Aug 2026, 15:53","","Bench Press (Barbell)",,"",2,"normal",20,14,,,
        "Leg Day 🔥","17 Aug 2026, 15:17","17 Aug 2026, 15:53","","Plank",,"",0,"normal",,,,30,
        "Leg Day 🔥","17 Aug 2026, 15:17","17 Aug 2026, 15:53","","Bench Press (Barbell)",,"",0,"normal",12,10,,,
        "Leg Day 🔥","17 Aug 2026, 15:17","17 Aug 2026, 15:53","","Bent Over Row (Dumbbell)",1,"Rowed it.",1,"failure",24,12,0.5,90,9.5
        "Cardio","18 Aug 2026, 09:00",,"no end time row","Run (Treadmill)",,"",0,"normal",,,3.2,600,
    """.trimIndent()

    @Test
    fun `groups rows into workouts by title and start time`() {
        val workouts = WorkoutCsvReader.parse(csv)
        assertEquals(2, workouts.size)
        // Newest first.
        assertEquals("Cardio", workouts.first().title)
    }

    @Test
    fun `exercises preserve first-appearance order and sets are sorted by set_index`() {
        val workout = WorkoutCsvReader.parse(csv).first { it.title == "Leg Day 🔥" }
        assertEquals(listOf("Bench Press (Barbell)", "Plank", "Bent Over Row (Dumbbell)"), workout.exercises.map { it.title })
        val bench = workout.exercises.first()
        assertEquals(12.0, bench.sets[0].weight_kg)
        assertEquals(20.0, bench.sets[1].weight_kg)
    }

    @Test
    fun `blank numeric cells become null and distance is converted to meters`() {
        val workout = WorkoutCsvReader.parse(csv).first { it.title == "Leg Day 🔥" }
        val plank = workout.exercises.first { it.title == "Plank" }
        val set = plank.sets.single()
        assertNull(set.weight_kg)
        assertNull(set.reps)
        assertEquals(30, set.duration_seconds)

        val row = workout.exercises.first { it.title == "Bent Over Row (Dumbbell)" }.sets.single()
        assertEquals(500, row.distance_meters)
        assertEquals(9.5, row.rpe)
    }

    @Test
    fun `timestamps become utc instants and missing end falls back to start`() {
        val workouts = WorkoutCsvReader.parse(csv)
        val legDay = workouts.first { it.title == "Leg Day 🔥" }
        assertEquals("2026-08-17T15:17:00Z", legDay.start_time.toString())
        assertEquals("2026-08-17T15:53:00Z", legDay.end_time.toString())

        val cardio = workouts.first { it.title == "Cardio" }
        assertEquals(cardio.start_time, cardio.end_time)
    }

    @Test
    fun `ids are deterministic and template ids match across workouts`() {
        val workouts = WorkoutCsvReader.parse(csv)
        val benchIds = workouts
            .flatMap { it.exercises }
            .filter { it.title == "Bench Press (Barbell)" }
            .map { it.exercise_template_id }
            .distinct()
        assertEquals(1, benchIds.size)

        // Same parse run and re-parse produce identical workout IDs.
        val again = WorkoutCsvReader.parse(csv)
        assertEquals(workouts.map { it.id }, again.map { it.id })
        assertEquals(workouts.flatMap { it.exercises }.map { it.exercise_template_id },
            again.flatMap { it.exercises }.map { it.exercise_template_id })
    }

    @Test
    fun `rows with unparseable timestamps are skipped`() {
        val bad = csv + "\n\"Bad\",\"not a date\",\"\",,\"X\",,\"\",0,\"normal\",1,1,,, "
        val workouts = WorkoutCsvReader.parse(bad)
        assertNull(workouts.find { it.title == "Bad" })
    }
}
