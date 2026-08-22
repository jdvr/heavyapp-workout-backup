plugins {
    alias(libs.plugins.kotlin.multiplatform)
}


kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(ktorLibs.client.core)
            implementation(project(":core"))
        }

    }
}
