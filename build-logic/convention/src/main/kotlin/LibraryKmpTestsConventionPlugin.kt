import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import koo.buildlogic.library
import koo.buildlogic.libs
import koo.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

@Suppress("unused")
class LibraryKmpTestsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply(libs.pluginId("android-kmp-library"))
                apply(libs.pluginId("kotlin-multiplatform"))
            }

            extensions.configure<KotlinMultiplatformExtension> {
                applyDefaultHierarchyTemplate()

                targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach {
                    withHostTest {
                        // no-op
                    }

                    withDeviceTestBuilder {
                        sourceSetTreeName = "test"
                    }.configure {
                        instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    }
                }

                jvm()

                sourceSets.apply {
                    commonTest.dependencies {
                        implementation(libs.library("kotlin-test"))
                        implementation(libs.library("kotlinx-coroutines-testing"))
                        implementation(libs.library("willowtreeapps-assertk"))
                    }

                    getByName("androidHostTest") {
                        dependencies {
                            implementation(libs.library("junit4"))
                        }
                    }

                    getByName("androidDeviceTest") {
                        dependencies {
                            implementation(libs.library("junit4"))
                            implementation(libs.library("androidx-test-junit"))
                            implementation(libs.library("androidx-test-espresso-core"))
                        }
                    }

                }
            }
        }
    }
}
