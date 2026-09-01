# Fix `./gradlew jvmTest` after per-platform OpenCV download (1917ff7)

- Date: 2026-09-01
- Branch: `fix/gradle/download-relevant-opencv-platform-deps`
- Related commits: `1917ff7` (download only relevant OpenCV JVM platform), `55b4fb0` (creation of custom source sets)

## Symptom

`./gradlew jvmTest` fails with, for every suite that touches a bytedeco `Mat`
(`PaddleOcrServiceJvmTest`, `PaddleOcrDetectionServiceJvmTest`,
`PaddleOcrRecognitionServiceJvmTest`):

```
java.lang.UnsatisfiedLinkError: 'void org.bytedeco.javacpp.BytePointer.allocateArray(long)'
    at org.bytedeco.javacpp.BytePointer.allocateArray(Native Method)
```

`OcrPipelineTest` (no native code) passes; everything else fails at the first
`BytePointer` allocation.

## Root cause

Bytedeco presets ship Java classes in the **main jar** and native libraries in
**platform-classified jars**. Commit `1917ff7` replaced
`org.bytedeco:opencv-platform` with bare `org.bytedeco:opencv` plus a single
host-classified `opencv` jar. That reduces the download, but bare `opencv`'s
POM only pulls the `openblas` and `javacpp` **main jars** (Java classes), never
their platform natives:

- `opencv:4.14.0-1.5.14` POM → `openblas:0.3.34-1.5.14` (main) → `javacpp:1.5.14` (main)
  (`numpy`/`android` deps are optional and not resolved)
- `opencv:4.14.0-1.5.14:macosx-arm64` → only OpenCV's own natives

The old `opencv-platform` chain delivered, per platform, three native jars:

- `javacpp:<v>:<platform>` → `libjnijavacpp` (required by `BytePointer`/all presets)
- `openblas:<v>:<platform>` → `libopenblas` (linked by `jniopencv_core`)
- `opencv:<v>:<platform>` → `libjniopencv_core` + OpenCV libs

Verified against Maven Central (4.14.0-1.5.14 publishes no Gradle module
metadata, so resolution is POM-only): classified native jars exist for
`javacpp-1.5.14-<platform>` and `openblas-0.3.34-1.5.14-<platform>` with the
same platform names `jvmClassifier()` produces (e.g. `macosx-arm64`).

Missing `jnijavacpp` is the immediate failure; missing `libopenblas` would be
the next one once `jniopencv_core` loads. Both must be added.

Additionally, the three presets publish **different** platform sets at the
pinned versions, which `jvmClassifier()` must respect:

- `javacpp:1.5.14`: `windows-x86_64`, `windows-arm64`, `macosx-x86_64`,
  `macosx-arm64`, `linux-x86_64`, `linux-arm64`, `linux-ppc64le`, `linux-riscv64`
- `openblas:0.3.34-1.5.14` / `opencv:4.14.0-1.5.14`: `windows-x86_64`,
  `macosx-x86_64`, `macosx-arm64`, `linux-x86_64`, `linux-arm64`
- `linux-armhf` (32-bit ARM) and big-endian `ppc64` are published by none of
  the three.

## Fix (2 files)

### 1. `gradle/libs.versions.toml`

Pin the javacpp/openblas coordinates used for the JVM natives, next to the
existing `javacv`/`opencv-jvm` entries:

```toml
[versions]
javacpp = "1.5.14"
openblas = "0.3.34-1.5.14"

[libraries]
javacpp-jvm = { group = "org.bytedeco", name = "javacpp", version.ref = "javacpp" }
openblas-jvm = { group = "org.bytedeco", name = "openblas", version.ref = "openblas" }
```

### 2. `build-logic/convention/src/main/kotlin/OpencvJvmConventionPlugin.kt`

Two fixes in one file:

**(a) Add the missing natives** — each preset's main jar plus its
host-classified native jar (the per-platform equivalent of what
`opencv-platform` delivered).

**(b) Fix `jvmClassifier`** — model each preset's actually-published desktop
platforms and fail configuration with an actionable error instead of a cryptic
"Could not find artifact" resolution failure.

```kotlin
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
```

Behavior notes: an `arm`/`armv7` (32-bit) or big-endian `ppc64` JVM now fails
fast with a clear "Unsupported architecture" message instead of resolving a
nonexistent `linux-armhf` classifier; `windows-arm64`/`linux-ppc64le`/
`linux-riscv64` hosts still get `javacpp` natives but fail with a clear
per-preset error for `openblas`/`opencv` (they genuinely aren't published for
those platforms — an x86_64 JVM under emulation still works because `os.arch`
reflects the JVM).

`implementation` (not `runtimeOnly`) keeps parity with the existing code and
ensures the natives flow to project consumers via `runtimeElements` and to
published JVM POMs.

### No changes needed

The six module `build.gradle.kts` files (`kt-ocr-onnx`, `kt-ocr-onnx-core`,
`kt-ocr-onnx-detection`, `kt-ocr-onnx-recognition`, `detection/detection-core`,
`recognition/recognition-core`) and `build-logic/convention/build.gradle.kts`
need no changes — they all pick this up via the `koo.opencv.jvm.platform`
plugin, which is why the fix lives in the convention plugin.

## Verification

1. `./gradlew jvmTest` — must pass (fails today with the UnsatisfiedLinkError
   above). First run downloads only the host's natives: `javacpp` ~39KB +
   `openblas` ~12.7MB (+ already-resolved `opencv` ~25MB) instead of every
   platform.
2. `./gradlew spotlessCheck` — formatting of the edited Kotlin file (run
   `spotlessApply` if it complains).

No public API changes, so no `updateKotlinAbi` run needed.

## Notes / known limitations (out of scope)

- Published JVM POMs will pin the build machine's classifier (e.g.
  `macosx-arm64`), so consumers on other platforms get the wrong natives —
  pre-existing since `1917ff7`. The official
  `org.bytedeco.gradle-javacpp-platform` Gradle plugin could resolve this later
  if published-artifact portability matters.
- `val jvmCommonMain by creating` in `detection/detection-core` and
  `recognition/recognition-core` still uses the pattern `55b4fb0` replaced with
  `create("jvmCommonMain")` in `kt-ocr-onnx`. Configuration currently succeeds
  with it, so it is left untouched.
