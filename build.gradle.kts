plugins {
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
}

tasks.register<Delete>("clean") {
    group = "build"
    description = "Deletes build directory"

    delete(rootProject.layout.buildDirectory)
}

version = providers.environmentVariable("RELEASE_TAG")
    .map { it.removePrefix("v") }
    .getOrElse("1.0.0")

subprojects {
    version = rootProject.version
}
