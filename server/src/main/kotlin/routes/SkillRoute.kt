package dev.juanvega.routes

import dev.juanvega.Resources
import dev.juanvega.source.BodyMeasurementSource
import dev.juanvega.source.ExerciseTemplateSource
import dev.juanvega.source.RoutineFolderSource
import dev.juanvega.source.RoutineSource
import dev.juanvega.source.UserInfoSource
import dev.juanvega.source.WorkoutSource
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.resources.Resource
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.CompositeRouteSelector
import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.OptionalParameterRouteSelector
import io.ktor.server.routing.ParameterRouteSelector
import io.ktor.server.routing.PathSegmentOptionalParameterRouteSelector
import io.ktor.server.routing.PathSegmentParameterRouteSelector
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.get
import io.ktor.server.routing.getAllRoutes
import io.ktor.server.routing.path
import io.ktor.server.routing.routingRoot
import io.ktor.util.AttributeKey
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.time.Clock

private val markdownContentType = ContentType.parse("text/markdown")

/**
 * Ktor route attribute that holds a human-friendly description for a skill endpoint.
 *
 * Prefer attaching docs co-located with the route definition:
 * ```
 * get<Resources.Workouts> { ... }.withSkillDescription("All workouts, newest first ...")
 * // or
 * route { withSkillDescription("..."); get<Resources.Workouts> { ... } }
 * ```
 * The skill handler discovers these at request time via lineage search, so no
 * central static map is needed — adding a route + its description keeps
 * `GET /skill.md` up to date.
 */
internal val SkillDescriptionKey: AttributeKey<String> = AttributeKey("SkillDescription")
internal val SkillSummaryKey: AttributeKey<String> = AttributeKey("SkillSummary")

fun Route.withSkillDescription(description: String, summary: String? = null): Route {
    attributes.put(SkillDescriptionKey, description)
    if (summary != null) attributes.put(SkillSummaryKey, summary)
    return this
}

/**
 * Registers the skill endpoint.
 *
 * `GET /skill.md` (and aliases) returns `text/markdown` in SKILL.md
 * standard. The endpoint table is built at request time from Ktor routing
 * metadata (`routingRoot.getAllRoutes()` + `path` + selector introspection)
 * and from per-route [SkillDescriptionKey] / OpenAPI `describe` attributes.
 * No static `endpointDescriptions` map — docs live next to routes.
 */
fun Route.skillRoute(
    workouts: WorkoutSource,
    exerciseTemplates: ExerciseTemplateSource,
    routineFolders: RoutineFolderSource,
    routines: RoutineSource,
    bodyMeasurements: BodyMeasurementSource,
    userInfo: UserInfoSource,
) {
    fun Route.registerSkillHandler(path: String) {
        get(path) {
            val md = generateSkillMarkdown(
                application = call.application,
                workouts = workouts,
                exerciseTemplates = exerciseTemplates,
                routineFolders = routineFolders,
                routines = routines,
                bodyMeasurements = bodyMeasurements,
                userInfo = userInfo,
            )
            call.respondText(md, markdownContentType)
        }.withSkillDescription(
            summary = "Skill file",
            description = "This skill file — markdown guide for AI agents. `Content-Type: text/markdown`. Always up-to-date via Ktor routing introspection + per-route SkillDescription attributes.",
        )
    }

    registerSkillHandler("/skill.md")
    registerSkillHandler("/SKILL.md")
    registerSkillHandler("/v1/skill.md")
    registerSkillHandler("/v1/SKILL.md")
}

// ---------------------------------------------------------------------------
// Dynamic description resolution — Ktor metadata only
// ---------------------------------------------------------------------------

private data class EndpointDoc(
    val method: String,
    val path: String,
    val description: String,
    val available: Boolean,
)

/**
 * Resource class index for auto-generating fallback docs.
 * Key = @Resource path pattern (e.g. "/v1/workouts/{id}"), value = KClass.
 */
private val resourceIndex: Map<String, KClass<*>> by lazy {
    Resources::class.nestedClasses.mapNotNull { kclass ->
        val ann = kclass.findAnnotation<Resource>() ?: return@mapNotNull null
        ann.path to kclass
    }.toMap()
}

private fun isAvailable(
    path: String,
    workouts: WorkoutSource,
    exerciseTemplates: ExerciseTemplateSource,
    routineFolders: RoutineFolderSource,
    routines: RoutineSource,
    bodyMeasurements: BodyMeasurementSource,
    userInfo: UserInfoSource,
): Boolean = when {
    path.startsWith("/v1/workouts") -> workouts.isAvailable
    path.startsWith("/v1/exercise_history") -> workouts.isAvailable
    path.startsWith("/v1/exercise_templates") -> exerciseTemplates.isAvailable
    path.startsWith("/v1/routines") -> routines.isAvailable
    path.startsWith("/v1/routine_folders") -> routineFolders.isAvailable
    path.startsWith("/v1/body_measurements") -> bodyMeasurements.isAvailable
    path.startsWith("/v1/user") -> userInfo.isAvailable
    path == "/" || path.startsWith("/skill") || path.startsWith("/SKILL") || path.startsWith("/v1/skill") -> true
    path.startsWith("/openapi") -> true
    else -> true
}

// --- Ktor selector introspection ---

private fun RouteSelector?.findHttpMethod(): HttpMethod? {
    when (this) {
        is HttpMethodRouteSelector -> return method
        is CompositeRouteSelector -> {
            for (sub in subSelectors()) {
                sub.findHttpMethod()?.let { return it }
            }
        }
        else -> {}
    }
    return null
}

private fun RouteSelector.flatten(): List<RouteSelector> = when (this) {
    is CompositeRouteSelector -> subSelectors().flatMap { it.flatten() }
    else -> listOf(this)
}

private fun RoutingNode.extractMethod(): String {
    var cur: Route? = this
    while (cur != null) {
        cur.selector.findHttpMethod()?.let { return it.value }
        cur = cur.parent
    }
    val str = toString()
    Regex("\\(method:(\\w+)\\)").find(str)?.let { return it.groupValues[1] }
    return "GET"
}

private fun RoutingNode.findSkillDescriptionInLineage(): String? {
    var cur: Route? = this
    while (cur != null) {
        cur.attributes.getOrNull(SkillDescriptionKey)?.let { return it }
        cur = cur.parent
    }
    // Also try official OpenAPI describe fallback — read OperationDescribeAttributeKey if present
    // We look for any attribute that contains "OperationDescribe" in its name via string check,
    // to avoid hard Experimental API dependency when the plugin is not in use.
    var cursor: Route? = this
    while (cursor != null) {
        for (key in cursor.attributes.allKeys) {
            if (key.name == "OperationDescribe") {
                @Suppress("UNCHECKED_CAST")
                val ops = cursor.attributes.getOrNull(key as AttributeKey<List<Any>>)
                if (!ops.isNullOrEmpty()) {
                    // Best-effort: derive a textual hint from the stored lambdas count
                    return "Documented via Ktor OpenAPI `describe` (see `describe { summary/description }` on route). Path requires OpenAPI Operation introspection for full text."
                }
            }
        }
        cursor = cursor.parent
    }
    return null
}

private fun RoutingNode.collectParamSelectors(): Pair<List<String>, List<Pair<String, Boolean>>> {
    val pathParams = mutableListOf<String>()
    val queryParams = mutableListOf<Pair<String, Boolean>>()
    var cur: Route? = this
    while (cur != null) {
        val sels = cur.selector?.flatten().orEmpty()
        for (s in sels) {
            when (s) {
                is PathSegmentParameterRouteSelector -> pathParams += s.name
                is PathSegmentOptionalParameterRouteSelector -> pathParams += "${s.name} (optional)"
                is ParameterRouteSelector -> queryParams += s.name to false
                is OptionalParameterRouteSelector -> queryParams += s.name to true
            }
        }
        cur = cur.parent
    }
    return pathParams.distinct() to queryParams.distinctBy { it.first }
}

private fun RoutingNode.autoDescription(path: String, method: String): String {
    val kclass = resourceIndex[path]
        ?: resourceIndex.entries.find { (pattern, _) ->
            val regex = pattern.replace(Regex("\\{[^}]+}"), "[^/]+")
            Regex("^$regex$").matches(path)
        }?.value

    // Try Resources reflection for richer info
    if (kclass != null) {
        val ctor = kclass.primaryConstructor
        val placeholderNames = Regex("\\{([^}]+)}").findAll(path).map { it.groupValues[1] }.toSet()
        val allParams = ctor?.parameters?.mapNotNull { it.name } ?: emptyList()
        val pathParamNames = allParams.filter { it in placeholderNames }
        val queryParamNames = allParams.filter { it !in placeholderNames }
        val queryDetails = if (queryParamNames.isNotEmpty()) {
            val details = ctor!!.parameters.filter { it.name in queryParamNames }.joinToString(", ") { p ->
                val req = if (p.isOptional) "optional" else "required"
                "`${p.name}` ($req)"
            }
            " Query params: $details."
        } else ""

        val resourceHint = "Resource `${kclass.simpleName}`."
        val pathDetails = if (pathParamNames.isNotEmpty()) " Path params: ${pathParamNames.joinToString { "`$it`" }}." else ""
        return "Handles `$method $path`. $resourceHint$pathDetails$queryDetails Auto-discovered from Ktor Resources metadata — add `.withSkillDescription(...)` or `describe {}` on the route for richer docs."
    }

    // Fallback to selector-based auto doc
    val (pathParams, queryParams) = collectParamSelectors()
    val paramBits = buildList {
        if (pathParams.isNotEmpty()) add("Path params: ${pathParams.joinToString { "`$it`" }}")
        if (queryParams.isNotEmpty()) {
            add("Query params: ${queryParams.joinToString { (n, opt) -> "`$n` (${if (opt) "optional" else "required"})" }}")
        }
    }.joinToString(". ")

    val base = "Handles `$method $path`."
    val params = if (paramBits.isNotEmpty()) " $paramBits." else ""
    val hint = " Auto-discovered from Ktor routing metadata (RoutingNode.path + selector introspection) — attach `.withSkillDescription(...)` for richer docs."
    return base + params + hint
}

private fun RoutingNode.resolveDescription(path: String, method: String): String {
    findSkillDescriptionInLineage()?.let { return it }
    // Try to still surface a helpful fallback that mentions path pattern + params
    return autoDescription(path, method)
}

internal fun generateSkillMarkdown(
    application: Application,
    workouts: WorkoutSource,
    exerciseTemplates: ExerciseTemplateSource,
    routineFolders: RoutineFolderSource,
    routines: RoutineSource,
    bodyMeasurements: BodyMeasurementSource,
    userInfo: UserInfoSource,
): String {
    val now = Clock.System.now()

    val discovered: List<EndpointDoc> = try {
        val root: RoutingNode = application.routingRoot
        val nodes = root.getAllRoutes()
        nodes.mapNotNull { node ->
            val p = node.path
            if (p.isBlank()) return@mapNotNull null
            val method = node.extractMethod()
            val avail = isAvailable(p, workouts, exerciseTemplates, routineFolders, routines, bodyMeasurements, userInfo)
            val desc = node.resolveDescription(p, method) + if (!avail && !p.startsWith("/skill") && !p.startsWith("/openapi")) " Status when unavailable: `503 { error: \"<domain> source is not available yet\" }`." else ""
            EndpointDoc(method = method, path = p, description = desc, available = avail)
        }
            .distinctBy { "${it.method} ${it.path}" }
            .sortedWith(compareBy({ it.path }, { it.method }))
    } catch (_: IllegalStateException) {
        // Routing not installed (unit tests without routing) — fall back to Resources reflection
        resourceIndex.entries.map { (path, kclass) ->
            val method = if (path == "/v1/workouts/import") "POST" else "GET"
            val avail = isAvailable(path, workouts, exerciseTemplates, routineFolders, routines, bodyMeasurements, userInfo)
            // Best-effort description from reflection (still Ktor Resources metadata, no static map)
            val placeholderNames = Regex("\\{([^}]+)}").findAll(path).map { it.groupValues[1] }.toSet()
            val ctor = kclass.primaryConstructor
            val allParamNames = ctor?.parameters?.mapNotNull { it.name } ?: emptyList()
            val queryPart = allParamNames.filter { it !in placeholderNames }.let { qs ->
                if (qs.isEmpty()) "" else " Query params: ${qs.joinToString { "`$it`" }}."
            }
            val desc = "Resource `${kclass.simpleName}` at `$path`.$queryPart Auto-discovered from Ktor Resources metadata."
            EndpointDoc(method, path, desc, avail)
        } + listOf(
            EndpointDoc("GET", "/", "Health check — returns plain text `heavyapp-workout-backup`.", true),
            EndpointDoc("GET", "/skill.md", "This skill file — `Content-Type: text/markdown` via Ktor routing introspection.", true),
            EndpointDoc("GET", "/openapi", "OpenAPI JSON spec (Ktor OpenAPI plugin).", true),
        ).distinctBy { "${it.method} ${it.path}" }.sortedWith(compareBy({ it.path }, { it.method }))
    }

    val tableRows = discovered.joinToString("\n") { ep ->
        val status = if (ep.available) "✅ `200`" else "⏳ `503` (source not wired)"
        val safeDesc = ep.description.replace("|", "\\|").replace("\n", " ")
        "| `${ep.method}` | `${ep.path}` | $status | $safeDesc |"
    }

    val total = discovered.size
    val availableCount = discovered.count { it.available }
    val unavailableCount = total - availableCount

    return buildString {
        appendLine("---")
        appendLine("name: heavyapp-workout-parser")
        appendLine("description: Hevy-compatible workout API — CSV-backed, Ktor 3.5 + Kotlin Multiplatform. Use this skill to discover endpoints, query workouts, handle imports, and understand availability semantics.")
        appendLine("version: 1.0.0")
        appendLine("base_url: http://localhost:8080")
        appendLine("generated_at: $now")
        appendLine("source: Ktor routing introspection (Application.routingRoot.getAllRoutes() + per-route SkillDescriptionKey + Resources reflection)")
        appendLine("---")
        appendLine()
        appendLine("# HeavyApp Workout Parser — API Skill")
        appendLine()
        appendLine("> **For AI agents and humans.** This `SKILL.md` is served live from the running server (`GET /skill.md`) and is **auto-generated from Ktor routing metadata**, so the endpoint table never drifts from the actual registered routes. Per-route docs are attached via `Route.withSkillDescription(...)` (a Ktor `AttributeKey` on the route) or standard `describe {}` — the skill handler reads them via lineage search; undiscovered routes fall back to an auto-generated description from `Resources` reflection + selector introspection.")
        appendLine()
        appendLine("## How this file stays up to date")
        appendLine()
        appendLine("- **Ktor metadata — endpoint list:** at request time the handler walks `application.routingRoot.getAllRoutes(): List<RoutingNode>` and reads each node's `path` (`Route.path`) and HTTP method (`HttpMethodRouteSelector` / `CompositeRouteSelector`). Any `routing { get/post<Resources.X> { } }` added anywhere is automatically listed.")
        appendLine("- **Ktor metadata — descriptions:** each route attaches a description via `Route.withSkillDescription(summary, description)` (stores a `AttributeKey<String>` on the `Route`) or standard `describe { summary/description }`. The skill handler resolves them by walking `Route.lineage()` / parent chain and reading `SkillDescriptionKey` (and `OperationDescribeAttributeKey` as fallback). No central `endpointDescriptions` map.")
        appendLine("- **Ktor metadata — parameters:** path/query params are derived from the route's `PathSegmentParameterRouteSelector`, `ParameterRouteSelector` / `OptionalParameterRouteSelector` selectors **and** from `Resources` reflection (`@Resource(path)` + constructor parameters), so adding `@Resource(\"/v1/foo/{id}\") data class Foo(val id: String, val filter: String? = null)` automatically documents params.")
        appendLine("- **Availability:** derived live from `DataSource.isAvailable` flags injected into `skillRoute(...)`.")
        appendLine("- **Trigger:** `GET /skill.md`, `GET /SKILL.md`, `GET /v1/skill.md` all return `Content-Type: text/markdown`.")
        appendLine()
        appendLine("## Quick start")
        appendLine()
        appendLine("```bash")
        appendLine("# Health check")
        appendLine("curl http://localhost:8080/")
        appendLine()
        appendLine("# List workouts (newest first)")
        appendLine("curl http://localhost:8080/v1/workouts | jq .")
        appendLine()
        appendLine("# Single workout")
        appendLine("curl http://localhost:8080/v1/workouts/<id> | jq .")
        appendLine()
        appendLine("# Events since timestamp (ISO 8601)")
        appendLine("curl \"http://localhost:8080/v1/workouts/events?since=2026-08-17T00:00:00Z\" | jq .")
        appendLine()
        appendLine("# Exercise history (derived from workouts)")
        appendLine("curl \"http://localhost:8080/v1/exercise_history/<templateId>?start_date=2026-01-01T00:00:00Z&end_date=2026-12-31T00:00:00Z\" | jq .")
        appendLine()
        appendLine("# Count")
        appendLine("curl http://localhost:8080/v1/workouts/count")
        appendLine()
        appendLine("# Import CSV (custom endpoint)")
        appendLine("curl -X POST http://localhost:8080/v1/workouts/import \\")
        appendLine("  -H \"Content-Type: text/csv\" \\")
        appendLine("  --data-binary @workouts.csv")
        appendLine()
        appendLine("# This skill file itself")
        appendLine("curl -H \"Accept: text/markdown\" http://localhost:8080/skill.md")
        appendLine("```")
        appendLine()
        appendLine("## Base URL & conventions")
        appendLine()
        appendLine("- **Base URL:** `http://localhost:8080` (override via `ktor.deployment.port` / `-P:ktor.deployment.port=9090`).")
        appendLine("- **Auth:** none — no `api-key` header, no pagination (diverges from Hevy's public API on purpose).")
        appendLine("- **Content negotiation:** JSON via `ContentNegotiation.json(ApiJson)` where `ApiJson = Json { encodeDefaults = false; ignoreUnknownKeys = true; explicitNulls = false }`. `null`s are omitted, unknown keys ignored.")
        appendLine("- **CORS:** permissive (`anyHost`, allows `Authorization`, `MyCustomHeader`, methods `OPTIONS, PUT, DELETE, PATCH`).")
        appendLine("- **Compression, DefaultHeaders (`X-Engine: Ktor`), OpenTelemetry (`KtorServerTelemetry` + `getOpenTelemetry(...)`)** are enabled.")
        appendLine("- **Status codes:** `200` JSON, `404 { error }`, `503 { error: \"<domain> source is not available yet\" }` for unwired domains, `400` for bad import, `500` fallback via `StatusPages`.")
        appendLine("- **OpenAPI:** `GET /openapi` serves the generated spec; Swagger UI at `GET /openapi` (configured in `Http.kt`).")
        appendLine()
        appendLine("## Endpoints (live — from Ktor metadata)")
        appendLine()
        appendLine("_Generated at `$now` — $total endpoints discovered, $availableCount available, $unavailableCount awaiting a data source. Method + path from `RoutingNode.path` + `HttpMethodRouteSelector`; description from `SkillDescriptionKey` on the route (or `Resources` + selector auto-gen); availability from `DataSource.isAvailable`._")
        appendLine()
        appendLine("| Method | Path | Status | Description |")
        appendLine("|---|---|---|---|")
        if (tableRows.isNotBlank()) appendLine(tableRows) else appendLine("| `GET` | `/` | ✅ `200` | Health check |")
        appendLine()
        appendLine("### Notes on unwired domains")
        appendLine()
        appendLine("Routes for `exercise_templates`, `routines`, `routine_folders`, `body_measurements`, `user/info` are registered but their sources are `UnavailableSources.*` (`isAvailable == false`). They respond `503 { error }` until a real `*Source` implementation is provided and wired in `Application.apiModule(...)` (see `AGENT.md` → _Extending with new sources_). No code change in route handlers is needed when wiring — the same handler will serve the new source.")
        appendLine()
        appendLine("## Data source (CSV)")
        appendLine()
        appendLine("- **Format:** Hevy export CSV (`example.csv`). Loaded at startup and reloaded every `heavyapp.refreshSeconds` (default 60s). Failed reload keeps last good snapshot.")
        appendLine("- **Configure:** `HEAVYAPP_DATA_FILE=example.csv ./gradlew :server:run` or `--args=\"-P:heavyapp.dataFile=example.csv\"` or `application.conf: heavyapp.dataFile`.")
        appendLine("- **Models:** `Workout`, `WorkoutExercise`, `WorkoutSet`, `Routine`, `RoutineFolder`, `ExerciseTemplate`, `BodyMeasurement`, `UserInfo` in `:core` (`commonMain`). `WorkoutSource.workoutEvents(since)` filters `updated_at >= since`; `exercise_history` is derived from workouts (deterministic template IDs).")
        appendLine()
        appendLine("## Error handling")
        appendLine()
        appendLine("```json")
        appendLine("// 404")
        appendLine("{\"error\": \"Workout <id> not found\"}")
        appendLine("// 503")
        appendLine("{\"error\": \"routine source is not available yet\"}")
        appendLine("// 400 (import)")
        appendLine("{\"error\": \"Request body must contain CSV data\"}")
        appendLine("```")
        appendLine()
        appendLine("## Extending the API")
        appendLine()
        appendLine("1. Add a new `@Resource(\"/v1/foo\")` in `Resources.kt`.")
        appendLine("2. Add a `FooSource : DataSource` interface in `:core/source/Sources.kt` and an implementation (or `UnavailableSources.foo()` placeholder).")
        appendLine("3. Add a route file `routes/FooRoute.kt` using `get<Resources.Foo> { ... }.withSkillDescription(\"...\", \"...\")` (or `describe { summary/description }`).")
        appendLine("4. Wire it in `Application.apiModule(...)` — the skill endpoint will automatically list it on the next `GET /skill.md` (via `getAllRoutes()`), with its description discovered from the route's `SkillDescriptionKey` / `Resources` reflection, no central map edit needed.")
        appendLine()
        appendLine("## Verification")
        appendLine()
        appendLine("```bash")
        appendLine("./gradlew :server:test   # includes skill endpoint tests")
        appendLine("./gradlew :server:run   # then curl http://localhost:8080/skill.md")
        appendLine("./gradlew build")
        appendLine("```")
        appendLine()
        appendLine("## References")
        appendLine()
        appendLine("- Hevy API docs (upstream inspiration, not affiliated): https://api.hevyapp.com/docs/")
        appendLine("- This project's `README.md`, `AGENT.md`, `server/src/main/kotlin/Resources.kt`, `routes/*`, `core/src/commonMain/kotlin/dev/juanvega/source/Sources.kt`")
        appendLine("- Skill standard: `SKILL.md` with YAML frontmatter (`name`, `description`) — served as `text/markdown` for agent consumption.")
        appendLine("- Ktor metadata used: `Application.routingRoot`, `RoutingNode.getAllRoutes()`, `Route.path`, `RouteSelector` (`HttpMethodRouteSelector`, `PathSegment*`, `ParameterRouteSelector`), `Route.attributes` + `AttributeKey`, `Resources` `@Resource` reflection, OpenAPI `OperationDescribeAttributeKey`.")
        appendLine()
        appendLine("---")
        appendLine("_Served by `SkillRoute.kt` via Ktor introspection. Attach `.withSkillDescription(...)` / `describe {}` co-located with each route for richer docs; add routes and they appear automatically._")
    }
}
