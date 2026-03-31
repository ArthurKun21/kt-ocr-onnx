plugins {
    `kotlin-dsl`
}

group = "koo.buildlogic"

dependencies {
    implementation(libs.androidx.gradle)
    implementation(libs.kotlin.gradle)
    implementation(libs.compose.compiler.gradle)
    implementation(libs.compose.gradle)
    implementation(libs.spotless.gradle)
}

gradlePlugin {
    plugins {
        register("codeLint") {
            id = "koo.code.lint"
            implementationClass = "CodeLintConventionPlugin"
        }
        register("compose") {
            id = "koo.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        register("kmpBase") {
            id = "koo.kmp.base"
            implementationClass = "KmpBaseConventionPlugin"
        }
        register("libraryKmp") {
            id = "koo.library.kmp"
            implementationClass = "LibraryKmpConventionPlugin"
        }
        register("libraryKmpTests") {
            id = "koo.library.kmp.tests"
            implementationClass = "LibraryKmpTestsConventionPlugin"
        }
    }
}
