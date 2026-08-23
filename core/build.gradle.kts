plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}


kotlin {
    jvmToolchain(21)
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.serializationJson)
            api(libs.opentelemetry.exporterOtlp)
            api(libs.opentelemetry.ktorInstrumentation)
            api(libs.opentelemetry.sdkAutoconfigure)
            api(libs.opentelemetry.semconv)
        }

    }
}
