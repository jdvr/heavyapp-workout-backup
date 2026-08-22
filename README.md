# heavyapp-workout-parser

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:

* [Ktor Documentation](https://ktor.io/docs/home.html)
* [Ktor GitHub page](https://github.com/ktorio/ktor)
* [Ktor Slack chat](https://app.slack.com/client/T09229ZC6/C0A974TJ9). [Request an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up).

## Features

Here's a list of features included in this project:

| Name                                                                                            | Description                                                                                             |
|-------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| [CORS](https://start.ktor.io/p/io.ktor/server-cors)                                             | Enables Cross-Origin Resource Sharing (CORS)                                                            |
| [Compression](https://start.ktor.io/p/io.ktor/server-compression)                               | Compresses responses using encoding algorithms like GZIP                                                |
| [Default Headers](https://start.ktor.io/p/io.ktor/server-default-headers)                       | Adds a default set of headers to HTTP responses                                                         |
| [OpenAPI](https://start.ktor.io/p/io.ktor/server-openapi)                                       | Serves OpenAPI documentation                                                                            |
| [Swagger](https://start.ktor.io/p/io.ktor/server-swagger)                                       | Serves Swagger UI for your project                                                                      |
| [Resources](https://start.ktor.io/p/io.ktor/server-resources)                                   | Provides type-safe routing                                                                              |
| [OpenTelemetry](https://start.ktor.io/p/io.opentelemetry.instrumentation/server-open-telemetry) | Instruments applications with distributed tracing, metrics, and logging for comprehensive observability |
| [Content Negotiation](https://start.ktor.io/p/io.ktor/server-content-negotiation)               | Provides automatic content conversion according to Content-Type and Accept headers                      |
| [kotlinx.serialization](https://start.ktor.io/p/io.ktor/server-kotlinx-serialization)           | Handles JSON serialization using kotlinx.serialization library                                          |

## Structure

This project includes the following modules:

| Path   | Description |
|--------|-------------|
|        | null        |
| client | null        |
| core   | null        |
| server | null        |

## Building & Running

To build or run the project, use one of the following tasks:

| Task                      | Description       |
|---------------------------|-------------------|
| `./gradlew :server:test`  | Run the tests     |
| `./gradlew :server:build` | Build the project |
| `./gradlew :server:run`   | Run the server    |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```
