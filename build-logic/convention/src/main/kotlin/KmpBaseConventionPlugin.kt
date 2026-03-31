import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import koo.buildlogic.AndroidConfig
import koo.buildlogic.configureCommonKotlinCompileOptions
import koo.buildlogic.kmpExperimentList
import koo.buildlogic.library
import koo.buildlogic.libs
import koo.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

@Suppress("unused")
class KmpBaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            configureCommonKotlinCompileOptions()

            with(pluginManager) {
                apply(libs.pluginId("android-kmp-library"))
                apply(libs.pluginId("kotlin-multiplatform"))
                apply("koo.code.lint")
            }

            extensions.configure<KotlinMultiplatformExtension> {
                applyDefaultHierarchyTemplate()

                targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach {
                    compileSdk = AndroidConfig.COMPILE_SDK
                    minSdk = AndroidConfig.MIN_SDK
                }

                jvm()

                sourceSets.apply {
                    commonMain.dependencies {
                        implementation(libs.library("logcat"))
                        implementation(libs.library("kotlinx-coroutines-core"))
                    }
                }

                compilerOptions.freeCompilerArgs.addAll(
                    kmpExperimentList,
                )
            }
        }
    }
}
