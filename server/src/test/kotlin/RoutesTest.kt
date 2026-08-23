package dev.juanvega

import dev.juanvega.model.Workout
import dev.juanvega.model.WorkoutExercise
import dev.juanvega.model.WorkoutSet
import dev.juanvega.source.UnavailableSources
import dev.juanvega.source.WorkoutSource
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.datetime.Instant
import kotlin.test.*

private class FakeWorkouts(private val list: List<Workout>) : WorkoutSource {
    override val isAvailable = true
    override suspend fun workouts(): List<Workout> = list
    override suspend fun workout(id: String): Workout? = list.find { it.id == id }
}

/** Source whose every access fails like the real unwired domains. */

private fun ApplicationTestBuilder.withApi(workouts: List<Workout>) =
    application {
        apiModule(
            workouts = FakeWorkouts(workouts),
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
}
