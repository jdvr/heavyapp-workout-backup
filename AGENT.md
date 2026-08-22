# AGENT.md — heavyapp-workout-parser

Guidance for AI agents (and humans) working in this repository.

## Project Overview

- **Stack:** Kotlin Multiplatform (KMP) + Ktor 3.5.x, built with Gradle (Kotlin DSL) and the `ktorLibs` version catalog.
- **Modules:**
  - `:core` — shared multiplatform code (`commonMain`).
  - `:client` — Ktor client (multiplatform, `commonMain`).
  - `:server` — Ktor server (JVM only).
- **JVM:** The project runs on JVM 25 (see `.sdkmanrc`: `java=25-tem`). Toolchains are resolved via the foojay resolver.

## ⛔ Git Safety Rules (STRICT)

1. **NEVER run `git commit`, `git push`, or any variant of them unless the user explicitly asks** in the current conversation. This includes:
   - `git commit` / `git push` / `git merge` to a remote-tracked branch
   - Amending, reverting, or resetting commits that already exist
   - Any command that publishes state outside the local working tree
2. Staging files (`git add`) and inspecting history (`git status`, `git log`, `git diff`) is allowed.
3. If a task seems to require a commit but wasn't explicitly requested, finish the work, summarize it, and ask the user whether they want it committed/pushed.
4. Never use `--force` flags on git commands under any circumstances unless explicitly instructed.

## Kotlin Multiplatform Rules

The project targets the JVM at runtime, but **shared logic must stay platform-neutral Kotlin**:

1. **No Java-specific code in `common*` source sets** (`commonMain`, `commonTest`). Do not import `java.*`, `javax.*`, `jdk.*`, or use JVM-only APIs there.
2. Prefer Kotlin stdlib and multiplatform libraries over Java APIs:
   - Use `kotlin.io`, `kotlinx-datetime`, `kotlinx-coroutines`, `kotlinx-serialization` instead of `java.time`, `java.io`, etc.
   - Use `expect`/`actual` (or `optionalExpectation`) only when no multiplatform alternative exists; keep `actual` implementations minimal and put them in the correct source sets (`jvmMain`, etc.).
3. Keep `commonMain` as the default home for new code. Only put code in platform source sets when it genuinely needs platform APIs (e.g., server-only OpenTelemetry setup in `server/src/main`).
4. When adding a dependency, prefer KMP-compatible artifacts (check that it supports `commonMain`). Avoid libraries that are JVM-only unless used exclusively in JVM modules/source sets.
5. Do not use `System.out`, `Thread`, `Runtime`, reflection (`java.lang.reflect`), or file-system APIs directly in common code — use `co.touchlab.kermit`/SLF4J-appropriate logging abstractions, coroutines, and Ktor APIs instead.
6. Coroutines everywhere for async code — never block with `runBlocking` inside production paths (it's acceptable only in `main` or tests).

## Idiomatic Kotlin Rules

1. **Immutability first:** prefer `val` over `var`; prefer immutable collections (`List`, `Map`, `Set`) and read-only exposures from APIs.
2. **Null safety:** avoid `!!`. Prefer safe calls (`?.`), the Elvis operator (`?:`), early returns, and `require`/`check`/`error` for invariant validation.
3. **Data modeling:** use `data class` for DTOs/models, `sealed interface`/`sealed class` for closed hierarchies, `enum class` for fixed sets, `value class` for lightweight type-safe wrappers, and `object` for singletons.
4. **Prefer expressions over statements:** use `when` as an expression, scope functions (`let`, `apply`, `also`, `run`, `with`) judiciously — don't nest them deeply.
5. **Extension functions** for API augmentation instead of utility classes with static methods; keep them in well-named files (e.g., `Serialization.kt`, `Resources.kt` style already used here).
6. **Coroutines idioms:** suspend functions for async operations, structured concurrency via `coroutineScope`, never use `GlobalScope` in production code.
7. **Naming:** follow standard Kotlin conventions — PascalCase types, camelCase functions/properties, SCREAMING_SNAKE_CASE constants. No Hungarian notation.
8. Explicit visibility: default to `internal`/`private` where possible; expose only what's needed.

## Ktor Best Practices

1. **Routing:** define routes in dedicated files using the type-safe `Resources` plugin (`@Resource` classes, see `server/src/main/kotlin/Resources.kt`) rather than raw string paths.
2. **Modular application config:** split `Application.module` configuration into focused extension functions (`configureSerialization()`, `configureRouting()`, `configureMonitoring()`), one per file, as the existing structure does.
3. **Content negotiation:** use `kotlinx.serialization` (`ContentNegotiation.json(Json { ... })`) consistently on both client and server; keep JSON configuration identical across modules.
4. **Status codes:** return proper status codes via `call.respond(HttpStatusCode...)`; use exceptions + `StatusPages` for centralized error handling instead of ad-hoc try/catch in handlers.
5. **Client usage:** create/reuse a single configured `HttpClient` (see `client/src/commonMain/kotlin/HttpClient.kt`); don't instantiate clients per request. Close engines appropriately per platform lifecycle.
6. **Plugins:** install plugins (CORS, compression, monitoring/OpenTelemetry) in dedicated configure functions; keep business logic out of plugin setup.
7. **Config:** read configuration via `environment.config` / HOCON, never hardcode ports, hosts, or secrets.
8. **Logging:** use `call.application.log` / SLF4J (`Logback` is configured) — never `println` in production code.

## Observability (OpenTelemetry)

The project ships OpenTelemetry out of the box: dependencies are declared in `:core`'s `commonMain` (OTel SDK autoconfigure, OTLP exporter, Ktor 3.0 instrumentation, semantic conventions), and the server wires them via `configureOpenTelemetry()` using the `KtorServerTelemetry` plugin and the shared `getOpenTelemetry()` factory.

1. **Use the OTel SDK for all telemetry.** Tracing and metrics must go through OpenTelemetry — never hand-roll monitoring with timers, counters, or `println`. If a code path needs to be monitored, instrument it with OTel.
2. **Instrument every code path that should be monitored.** New routes, clients calls, parsers, background jobs, and external integrations must produce traces (and metrics where useful), not just logs.
3. **Reuse the shared SDK instance:** always obtain `OpenTelemetry` through `getOpenTelemetry(serviceName)` from `:core`; never create ad-hoc SDKs or exporters per call site.
4. **Prefer automatic instrumentation first:** rely on the Ktor instrumentation plugins (`KtorServerTelemetry` on the server, `KtorClientTelemetry` on the client) for HTTP spans; add manual spans (`Tracer.spanBuilder(...)` / `span.makeCurrent()` or the Kotlin extension idioms) only for meaningful sub-operations inside handlers.
5. **Metrics:** use the OTel Metrics API (`Meter.counter`, `histogram`, gauges as appropriate) for rates/durations of business-relevant paths. Note: metric export is currently disabled in `getOpenTelemetry()` (`otel.metrics.exporter=none`) — re-enable/configure it before relying on metrics.
6. **Semantic conventions:** use attribute names from `io.opentelemetry.semconv` (e.g., `ServiceAttributes`) instead of inventing strings; custom attributes should be lowercase dotted names.
7. **Context propagation:** propagate trace context across coroutine boundaries and client calls (the Ktor instrumentation handles HTTP headers automatically); don't break context by launching detached coroutines.
8. Keep span names stable and low-cardinality (route templates/resource types, not IDs); put high-cardinality data in attributes.

## Build & Verification

- Build/verify changes with Gradle wrapper, e.g.: `./gradlew build` (or targeted tasks like `./gradlew :server:build`).
- Respect `.sdkmanrc` (JVM 25). Don't change toolchain versions without asking.
- Add/keep tests for changed behavior; JVM module tests live in `server/src/test`.
- After changes, report what you did and suggest verification steps — do not commit.
