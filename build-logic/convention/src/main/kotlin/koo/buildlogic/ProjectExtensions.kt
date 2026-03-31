package koo.buildlogic

import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val commonExperimentList = listOf(
    "-opt-in=kotlin.concurrent.atomics.ExperimentalAtomicApi",
)

val kmpExperimentList = listOf(
    "-Xexpect-actual-classes",
) + commonExperimentList

internal fun Project.configureCommonKotlinCompileOptions() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.addAll(
                commonExperimentList,
            )
            jvmTarget.set(AndroidConfig.JvmTarget)
        }
    }
}
