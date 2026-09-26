package dev.juanvega

import dev.juanvega.model.Workout
import dev.juanvega.model.WorkoutExercise
import dev.juanvega.model.WorkoutSet
import dev.juanvega.source.UnavailableSources
import dev.juanvega.source.WorkoutSource
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeWorkoutsSkill(private val list: List<Workout>) : WorkoutSource {
    override val isAvailable = true
    override suspend fun workouts(): List<Workout> = list
    override suspend fun workout(id: String): Workout? = list.find { it.id == id }
}

class SkillRouteTest {
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
    fun `skill md is served at all aliases as markdown`() = testApplication {
        application {
            apiModule(
                workouts = FakeWorkoutsSkill(listOf(workout)),
                exerciseTemplates = UnavailableSources.exerciseTemplates(),
                routineFolders = UnavailableSources.routineFolders(),
                routines = UnavailableSources.routines(),
                bodyMeasurements = UnavailableSources.bodyMeasurements(),
                userInfo = UnavailableSources.userInfo(),
            )
        }
        for (path in listOf("/skill.md", "/SKILL.md", "/v1/skill.md", "/v1/SKILL.md")) {
            val response = client.get(path)
            assertEquals(HttpStatusCode.OK, response.status, "GET $path")
            assertTrue(response.headers[io.ktor.http.HttpHeaders.ContentType]?.contains("text/markdown") == true, "content-type for $path: ${response.headers[io.ktor.http.HttpHeaders.ContentType]}")
            val body = response.bodyAsText()
            // Frontmatter and title
            assertTrue(body.contains("name: heavyapp-workout-parser"), body.take(500))
            assertTrue(body.contains("# HeavyApp Workout Parser"), body.take(2000))
            // Must contain Ktor introspection hint
            assertTrue(body.contains("Application.routingRoot.getAllRoutes()"), body)
            // Endpoint table must contain known routes discovered via Ktor metadata
            assertTrue(body.contains("/v1/workouts"), body)
            assertTrue(body.contains("/v1/workouts/count"), body)
            assertTrue(body.contains("/v1/workouts/events"), body)
            assertTrue(body.contains("/v1/exercise_history"), body)
            // Availability markers
            assertTrue(body.contains("503") || body.contains("available"), body)
            // Curl example
            assertTrue(body.contains("curl"), body)
        }
    }

    @Test
    fun `skill md reflects availability live`() = testApplication {
        application {
            apiModule(
                workouts = object : WorkoutSource {
                    override val isAvailable = false
                    override suspend fun workouts(): List<Workout> = emptyList()
                    override suspend fun workout(id: String): Workout? = null
                },
                exerciseTemplates = UnavailableSources.exerciseTemplates(),
                routineFolders = UnavailableSources.routineFolders(),
                routines = UnavailableSources.routines(),
                bodyMeasurements = UnavailableSources.bodyMeasurements(),
                userInfo = UnavailableSources.userInfo(),
            )
        }
        val body = client.get("/skill.md").bodyAsText()
        // When workouts source unavailable, workouts rows should show 503
        // We check that available vs unavailable counts are present
        assertTrue(body.contains("discovered"), body)
        assertTrue(body.contains("503"), body)
    }
}
