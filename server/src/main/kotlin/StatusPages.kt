package dev.juanvega

import dev.juanvega.routes.ErrorResponse
import dev.juanvega.source.SourceUnavailableException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*

fun Application.configureStatusPages() {
    install(StatusPages) {
        // Centralized error mapping per AGENT.md: no ad-hoc try/catch in handlers.
        exception<SourceUnavailableException> { call, cause ->
            call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse(cause.message ?: "source unavailable"))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception serving {}", call.request.uri, cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error"))
        }
    }
}
