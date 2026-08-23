package dev.juanvega

import io.ktor.http.*
import io.ktor.server.application.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry

fun Application.configureOpenTelemetry(openTelemetry: OpenTelemetry) {
    install(KtorServerTelemetry) {
        // The shared SDK instance from :core — never an ad-hoc SDK per call site.
        setOpenTelemetry(openTelemetry)
        capturedRequestHeaders(HttpHeaders.UserAgent)
    }
}
