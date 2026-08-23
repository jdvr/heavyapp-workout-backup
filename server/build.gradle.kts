plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}


application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

// Make paths passed via heavyapp.dataFile resolve relative to the repo root,
// not the :server project directory.
tasks.withType(JavaExec::class).configureEach {
    workingDir = rootDir
}

kotlin {
    jvmToolchain(21)
}
dependencies {
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.compression)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.cors)
    implementation(ktorLibs.server.defaultHeaders)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.openapi)
    implementation(ktorLibs.server.resources)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.routingOpenapi)
    implementation(ktorLibs.server.swagger)
    implementation(libs.logback.classic)
    implementation(project(":core"))

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}
