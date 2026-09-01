import koo.buildlogic.libs
import koo.buildlogic.moduleWithVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

@Suppress("unused")
class OpencvJvmConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("koo.kmp.base")
            }

            extensions.configure<KotlinMultiplatformExtension> {
                jvm()

                sourceSets.apply {
                    jvmMain.dependencies {
                        val classifier = jvmClassifier()
                        val openCvLib = libs.moduleWithVersion("opencv-jvm")
                        implementation(openCvLib)
                        implementation("${openCvLib}:${classifier}")
                    }
                }
            }
        }
    }
}

private fun jvmClassifier(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()

    val isWindows = os.contains("win")
    val isMacOs = os.contains("mac")
    val isLinux = os.contains("linux")
    val isArm = "aarch64" in arch || "arm64" in arch
    return when {
        isWindows -> if (isArm) "windows-arm64" else "windows-x86_64"
        isMacOs -> if (isArm) "macosx-arm64" else "macosx-x86_64"
        isLinux -> when {
            isArm -> "linux-arm64"
            "riscv64" in arch -> "linux-riscv64"
            "ppc64le" in arch || "ppc64" in arch -> "linux-ppc64le"
            arch == "arm" || arch.startsWith("armv7") -> "linux-armhf"
            else -> "linux-x86_64"
        }

        else -> error("Unsupported OS: ${os} ($arch)")
    }
}
