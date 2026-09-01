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
                        // Presets ship Java classes in the main jar and native libs in
                        // platform-classified jars; bare `opencv` only transitively pulls
                        // the javacpp/openblas main jars, so every preset needs its
                        // host-classified native jar on the runtime classpath.
                        val host = jvmClassifier()
                        for (preset in listOf("javacpp-jvm", "openblas-jvm", "opencv-jvm")) {
                            val module = libs.moduleWithVersion(preset)
                            implementation(module)
                            implementation("${module}:${presetClassifier(preset, host)}")
                        }
                    }
                }
            }
        }
    }
}

// Desktop platforms each preset publishes natives for at the versions pinned in
// libs.versions.toml; javacpp covers more platforms than the presets built on it.
// Revisit these sets when bumping javacv/javacpp/openblas.
private val presetPlatforms: Map<String, Set<String>> = mapOf(
    "javacpp-jvm" to setOf(
        "windows-x86_64", "windows-arm64",
        "macosx-x86_64", "macosx-arm64",
        "linux-x86_64", "linux-arm64", "linux-ppc64le", "linux-riscv64",
    ),
    "openblas-jvm" to setOf(
        "windows-x86_64", "macosx-x86_64", "macosx-arm64", "linux-x86_64", "linux-arm64",
    ),
    "opencv-jvm" to setOf(
        "windows-x86_64", "macosx-x86_64", "macosx-arm64", "linux-x86_64", "linux-arm64",
    ),
)

private fun presetClassifier(preset: String, host: String): String {
    val supported = presetPlatforms.getValue(preset)
    if (host !in supported) {
        error(
            "No JVM natives of '$preset' are published for this host ($host). " +
                "Published classifiers: ${supported.sorted().joinToString()}",
        )
    }
    return host
}

private fun jvmClassifier(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()

    val osName = when {
        os.contains("win") -> "windows"
        os.contains("mac") -> "macosx"
        os.contains("linux") -> "linux"
        else -> error("Unsupported OS: $os ($arch)")
    }

    val archName = when {
        arch == "x86_64" || arch == "amd64" -> "x86_64"
        arch == "aarch64" || arch == "arm64" -> "arm64"
        arch == "ppc64le" -> "ppc64le"
        arch == "riscv64" -> "riscv64"
        else -> error("Unsupported architecture: $arch ($os); no OpenCV JVM natives are published for it")
    }

    return "$osName-$archName"
}
