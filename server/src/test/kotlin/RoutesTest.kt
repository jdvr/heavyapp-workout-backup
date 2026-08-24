package dev.juanvega

import dev.juanvega.model.Workout
import dev.juanvega.model.WorkoutExercise
import dev.juanvega.model.WorkoutSet
import dev.juanvega.source.UnavailableSources
import dev.juanvega.source.WorkoutSource
import dev.juanvega.source.csv.CsvWorkoutSource
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.ContentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.*
import io.ktor.util.logging.KtorSimpleLogger

private class FakeWorkouts(private val list: List<Workout>) : WorkoutSource {
    override val isAvailable = true
    override suspend fun workouts(): List<Workout> = list
    override suspend fun workout(id: String): Workout? = list.find { it.id == id }
}

/** Source whose every access fails like the real unwired domains. */

private fun ApplicationTestBuilder.withApi(
    workouts: List<Workout>,
    csvSource: CsvWorkoutSource? = null,
) =
    application {
        apiModule(
            workouts = FakeWorkouts(workouts),
            csvSource = csvSource,
            exerciseTemplates = UnavailableSources.exerciseTemplates(),
            routineFolders = UnavailableSources.routineFolders(),
            routines = UnavailableSources.routines(),
            bodyMeasurements = UnavailableSources.bodyMeasurements(),
            userInfo = UnavailableSources.userInfo(),
        )
    }

class RoutesTest {

    private val benchId = "AAAAAAAA"
    private val workout = Workout(
        id = benchId,
        title = "Leg Day",
        start_time = Instant.parse("2026-08-17T15:17:00Z"),
        end_time = Instant.parse("2026-08-17T15:53:00Z"),
        updated_at = Instant.parse("2026-08-17T15:53:00Z"),
        created_at = Instant.parse("2026-08-17T15:53:00Z"),
        exercises = listOf(
            WorkoutExercise(
                index = 0,
                title = "Bench Press (Barbell)",
                exercise_template_id = benchId,
                sets = listOf(WorkoutSet(index = 0, type = "normal", weight_kg = 20.0, reps = 10)),
            ),
        ),
    )

    @Test
    fun `workouts list returns envelope without pagination fields`() = testApplication {
        withApi(listOf(workout))
        val response = client.get("/v1/workouts")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"workouts\""))
        assertFalse(body.contains("page_count"))
        assertFalse(body.contains("\"weight_kg\":null"))
    }

    @Test
    fun `workout count endpoint`() = testApplication {
        withApi(listOf(workout, workout.copy(id = "BBBBBBBB")))
        val response = client.get("/v1/workouts/count")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"workout_count":2}""", response.bodyAsText())
    }

    @Test
    fun `workout by id and unknown id`() = testApplication {
        withApi(listOf(workout))
        assertEquals(HttpStatusCode.OK, client.get("/v1/workouts/$benchId").status)
        val missing = client.get("/v1/workouts/NOPE")
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertTrue(missing.bodyAsText().contains("not found"))
    }

    @Test
    fun `events only ever contain updated entries`() = testApplication {
        withApi(listOf(workout))
        val body = client.get("/v1/workouts/events").bodyAsText()
        assertTrue(body.contains("\"type\":\"updated\""))
        assertFalse(body.contains("\"deleted\""))

        val filtered = client.get("/v1/workouts/events?since=2027-01-01T00:00:00Z")
        assertEquals("""{"events":[]}""", filtered.bodyAsText())
    }

    @Test
    fun `exercise history is derived from workouts by template id`() = testApplication {
        withApi(listOf(workout))
        val hit = client.get("/v1/exercise_history/$benchId")
        assertEquals(HttpStatusCode.OK, hit.status)
        assertTrue(hit.bodyAsText().contains("Leg Day"))

        val miss = client.get("/v1/exercise_history/ZZZZZZZZ")
        assertEquals("""{"exercise_history":[]}""", miss.bodyAsText())
    }

    @Test
    fun `unwired domains respond 503`() = testApplication {
        withApi(emptyList())
        listOf(
            "/v1/routines",
            "/v1/exercise_templates",
            "/v1/routine_folders",
            "/v1/body_measurements",
            "/v1/user/info",
            "/v1/body_measurements/2026-08-17",
        ).forEach { path ->
            val response = client.get(path)
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status, "GET $path")
            assertTrue(response.bodyAsText().contains("not available yet"), "GET $path")
        }
    }

    // ---- CSV import endpoint ----

    private val validCsvV2 = """
        "title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"
        "Fresh Data","23 Aug 2026, 10:00","23 Aug 2026, 10:30","","Bench Press (Barbell)",,"",0,"normal",40,8,,,
    """.trimIndent()

    @Test
    fun `import replaces the source file and backs up the previous one`() {
        val file = Files.createTempFile("import-test", ".csv")
        val original = """
            "title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_kg","reps","distance_km","duration_seconds","rpe"
            "Original","1 Jan 2026, 09:00","1 Jan 2026, 09:30","","Plank",,"",0,"normal",,,,30,
        """.trimIndent()
        Files.writeString(file, original)
        val source = CsvWorkoutSource(file, KtorSimpleLogger("test"))
        source.loadNow()

        testApplication {
            withApi(emptyList(), csvSource = source)
            val response = client.post("/v1/workouts/import") {
                contentType(ContentType.Text.CSV)
                setBody(validCsvV2)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("\"status\":\"imported\""), body)

            // Old content backed up, new content written.
            val backupName = Regex("import-test[0-9_-]+-backup\\.csv").find(body)!!.value
            val backup = file.resolveSibling(backupName)
            assertEquals(original, backup.readText())
            assertEquals(validCsvV2, file.readText())

            // Visible after the next load (the refresh loop's job in production).
            runBlocking { source.loadNow() }
            assertEquals(listOf("Fresh Data"), runBlocking { source.workouts().map { it.title } })
        }
        Files.walk(file.parent).use { paths ->
            paths.filter { it.fileName.toString().startsWith("import-test") }.forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun `invalid csv is rejected and leaves the file untouched`() {
        val file = Files.createTempFile("import-invalid", ".csv")
        val original = validCsvV2
        Files.writeString(file, original)
        val source = CsvWorkoutSource(file, KtorSimpleLogger("test"))
        source.loadNow()

        testApplication {
            withApi(emptyList(), csvSource = source)
            val response = client.post("/v1/workouts/import") {
                contentType(ContentType.Text.CSV)
                setBody("this is not,csv at all")
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(original, file.readText())
        }

        testApplication {
            withApi(emptyList(), csvSource = source)
            val blank = client.post("/v1/workouts/import") { contentType(ContentType.Text.CSV); setBody("") }
            assertEquals(HttpStatusCode.BadRequest, blank.status)
        }
        Files.deleteIfExists(file)
    }

    @Test
    fun `import without a configured csv source responds 503`() = testApplication {
        withApi(emptyList(), csvSource = null)
        val response = client.post("/v1/workouts/import") {
            contentType(ContentType.Text.CSV)
            setBody(validCsvV2)
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
    }
}
