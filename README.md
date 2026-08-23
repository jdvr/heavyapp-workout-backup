# heavyapp-workout-parser

A Hevy-compatible workout API that serves data from a CSV export file.

## Overview

- **Stack:** Kotlin Multiplatform + Ktor 3.5.x (JVM), Gradle Kotlin DSL, JVM 21 toolchain.
- **Modules:**
  - `:core` — shared, platform-neutral domain models and `DataSource` interfaces.
  - `:client` — Ktor client (multiplatform).
  - `:server` — Ktor server: CSV source implementation and the `/v1` API.

## API

The server mirrors Hevy's public API where it makes sense — no `api-key` header,
no pagination:

| Endpoint | Status | Description |
|---|---|---|
| `GET /v1/workouts` | 200 | All workouts, newest first |
| `GET /v1/workouts/count` | 200 | Total workout count |
| `GET /v1/workouts/{id}` | 200/404 | Single workout by ID |
| `GET /v1/workouts/events?since=<ISO 8601>` | 200 | Workouts updated since the given timestamp (`updated` events only; deletions are never emitted) |
| `GET /v1/exercise_history/{templateId}` | 200 | Set-level history for an exercise template, optional `start_date`/`end_date` |
| `GET /v1/routines`, `/v1/routine_folders`, `/v1/exercise_templates`, `/v1/body_measurements`, `/v1/user/info` | **503** | Domains whose data sources are not wired yet |

Write operations (`POST`/`PUT`) are not supported and respond `501`.

## Data source

Workouts come from a CSV file in Hevy's export format (see `example.csv`). The
file is loaded at startup and reloaded every 60 seconds; edits to the file show up
as new events on `/v1/workouts/events`. A failed reload keeps the last good snapshot.

Configure the file path via (in order of preference):

```bash
# environment variable
HEAVYAPP_DATA_FILE=example.csv ./gradlew :server:run

# command-line tag
./gradlew :server:run --args="-P:heavyapp.dataFile=example.csv"

# or edit heavyapp.dataFile in server/src/main/resources/application.conf
```

The refresh interval is configurable via `heavyapp.refreshSeconds` (default `60`).

## Building & Running

| Task                      | Description       |
|---------------------------|-------------------|
| `./gradlew :server:test`  | Run the tests     |
| `./gradlew build`         | Build everything  |
| `./gradlew :server:run`   | Run the server    |

If the server starts successfully:

```
2026-08-22 16:28:29.326 [main] INFO  Application - Loaded 5 workout(s) from example.csv
```

## Deploying on a server

The app ships as a self-contained fat JAR — only a **JRE/JDK 21 or newer** is
required on the target machine.

**1. Build the jar (on your machine):**

```bash
./gradlew :server:buildFatJar
# → server/build/libs/server-all.jar
```

**2. Copy to the server and run:**

```bash
scp server/build/libs/server-all.jar deploy@myserver:/opt/heavyapp/

ssh deploy@myserver
HEAVYAPP_DATA_FILE=/opt/heavyapp/workouts.csv java -jar /opt/heavyapp/server-all.jar
```

**Configuration options (all optional except the data file):**

| Setting | How | Default |
|---|---|---|
| CSV file path | `HEAVYAPP_DATA_FILE` env var, `-P:heavyapp.dataFile=…` CLI arg, or `application.conf` | unset (empty API) |
| Refresh interval | `-P:heavyapp.refreshSeconds=120` CLI tag | `60` seconds |
| HTTP port | `-P:ktor.deployment.port=9090` CLI tag | `8080` |

```bash
HEAVYAPP_DATA_FILE=/data/workouts.csv \
java -jar server-all.jar -P:ktor.deployment.port=9090 -P:heavyapp.refreshSeconds=30
```

**Run as a systemd service** (`/etc/systemd/system/heavyapp.service`):

```ini
[Unit]
Description=Hevy-compatible workout API
After=network.target

[Service]
User=deploy
WorkingDirectory=/opt/heavyapp
Environment=HEAVYAPP_DATA_FILE=/opt/heavyapp/workouts.csv
ExecStart=/usr/bin/java -jar /opt/heavyapp/server-all.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable --now heavyapp
journalctl -u heavyapp -f
```

Alternative packaging without a fat jar: `./gradlew :server:installDist` produces
`server/build/install/` with launch scripts (`bin/server`) and all libraries —
run it with `server/build/install/server/bin/server`.

## Extending with new sources

Each API domain is backed by an interface in `:core`
(`dev.juanvega.source.WorkoutSource`, `RoutineSource`, …). The CSV loader is just
one implementation of `WorkoutSource`; the currently unavailable domains respond
`503` until their sources are implemented and wired in
`Application.apiModule(...)`.
