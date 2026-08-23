plugins {
    alias(libs.plugins.kotlin.multiplatform)
}


kotlin {
    jvmToolchain(21)
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(ktorLibs.client.core)
            implementation(project(":core"))
        }

    }
}
