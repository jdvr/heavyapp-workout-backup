package dev.juanvega

import io.ktor.server.application.Application

fun Application.rootModule() {
    configureResources()
    configureOpenTelemetry()
    configureHttp()
    configureSerialization()
    configureRouting()
}
