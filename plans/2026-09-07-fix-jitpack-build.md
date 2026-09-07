# Fix JitPack build for 0.3.x — module identity conflict, version wiring, JBR provisioning

- Date: 2026-09-07
- Branch: `fix/jitpack-build`
- Failing JitPack builds: [0.3.0](https://jitpack.io/com/github/ArthurKun21/kt-ocr-onnx/0.3.0/build.log),
  [0.3.1](https://jitpack.io/com/github/ArthurKun21/kt-ocr-onnx/0.3.1/build.log),
  [0.3.2](https://jitpack.io/com/github/ArthurKun21/kt-ocr-onnx/0.3.2/build.log) (0.2.0 and earlier built ok)

## Symptoms

- **0.3.0 / 0.3.1**: `:kt-ocr-onnx:compileKotlinJvm` and `:kt-ocr-onnx:compileAndroidMain` fail with
  `Unresolved reference 'detection'`, `Cannot access class 'com.github.arthurkun.koo.detection.DetectionModel'`,
  cascading expect/actual mismatches. All `:detection:*` / `:recognition:*` modules themselves compile fine.
- **0.3.2** (after 51f5f26 stopped deleting `gradle/gradle-daemon-jvm.properties`): fails earlier —
  `Unable to download toolchain … Unpacked JDK archive does not contain a Java home` while provisioning
  JetBrains Runtime 25 from the 533 MB `jbrsdk_jcef-25.0.2-linux-x64-b329.111.tar.gz` archive.
- Publications land at version **1.0.0** instead of the requested 0.3.1 (JitPack passes
  `-Pversion`; the root build only reads the `RELEASE_TAG` env var).

## Root causes (all reproduced locally with the exact JitPack command
`./gradlew clean -Pgroup=com.github.ArthurKun21 -Pversion=0.3.1 assemble publishToMavenLocal`)

1. **Module identity conflict.** JitPack always passes `-Pgroup`/`-Pversion`. With any extra command-line
   Gradle property (`-Pgroup=…`, `-Pversion=0.3.1`, even `-Pfoo=bar`), Gradle treats the sibling projects
   sharing a leaf name as the same module: `dependencyInsight` shows
   `project ':detection:model-core' -> project ':recognition:model-core'` with selection reason
   *"By conflict resolution: between versions 1.0.0 and 1.0.0"*. One of the two wins, so the detection model
   modules' classes never reach `:kt-ocr-onnx`'s compile classpath → the exact compiler errors seen on JitPack.
   CI passes only because it never passes `-P` properties; local builds pass for the same reason. Introduced
   in 0.3.0 when the aggregator first gained `api(project(":detection:model-core"/":model-base"))`.
2. **Version wiring.** Root `build.gradle.kts` sets `version` from `RELEASE_TAG` env only, falling back to
   `1.0.0`; `-Pversion` is ignored, so JitPack artifacts are published under 1.0.0.
3. **JBR provisioning weight.** The daemon-JVM criteria file points Linux at the 533 MB `jbrsdk_jcef`
   archive; on JitPack's constrained container the download/unpack (retried after the first failure) dies with
   "Unpacked JDK archive does not contain a Java home". The slim `jbrsdk` variant of the same
   build (25.0.2-b329.111, same JetBrains vendor, no JCEF) is ~250 MB and unpacks far smaller.

## Fix

1. **Rename modules so every leaf project name is unique** (removes the identity collision for any
   `-P` property / group state; `project.name` is the only colliding part):
   - `:detection:model-core` → `:detection:detection-model-core`
   - `:detection:model-base` → `:detection:detection-model-base`
   - `:detection:model-v5-base` → `:detection:detection-model-v5-base`
   - `:recognition:model-core` → `:recognition:recognition-model-core`
   - `:recognition:model-base` → `:recognition:recognition-model-base`
   - `:recognition:model-v5-base` → `:recognition:recognition-model-v5-base`
   - `:recognition:model-v5-kr` → `:recognition:recognition-model-v5-kr`
   Directories are `git mv`-ed to match; `settings.gradle.kts` includes, all `project(":…")` references and
   `AGENTS.md` are updated. Published coordinates are unaffected (every module sets an explicit `artifactId`).
2. **Honor `-Pversion`** in root `build.gradle.kts`:
   `version = providers.gradleProperty("version").orElse(providers.environmentVariable("RELEASE_TAG").map { it.removePrefix("v") }).getOrElse("1.0.0")`
   (`-Pgroup` needs no wiring: the hardcoded `MAVEN_PUBLISH_GROUP_ID` already equals JitPack's group.)
3. **Point the Linux toolchain URLs at slim JBR** in `gradle/gradle-daemon-jvm.properties`:
   `https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-25.0.2-linux-x64-b329.111.tar.gz` and
   `…/jbrsdk-25.0.2-linux-aarch64-b329.111.tar.gz` (both verified reachable, ~250 MB). macOS/Windows URLs
   untouched.
4. **jitpack.yml**: keep the daemon-JVM criteria file on JitPack (already fixed on master in 51f5f26;
   this branch fast-forwards to pick it up).

## Verification

1. Clean worktree at the fix commit: exact JitPack command → `BUILD SUCCESSFUL`; POMs under
   `~/.m2/repository/com/github/ArthurKun21/kt-ocr-onnx/0.3.1/` carry version 0.3.1.
2. Immunity: same command plus an extra `-Pfoo=bar` → still passes.
3. `./gradlew spotlessCheck` and `./gradlew :kt-ocr-onnx:jvmTest` (per AGENTS.md).
4. On GitHub: re-tag (delete + re-push `0.3.2`, or push `0.3.3`) so JitPack rebuilds. The slim-JBR Linux
   provisioning itself can only be validated on JitPack (no Linux repro available locally).

## Notes

- `gradle/gradle-daemon-jvm.properties` is generated by `updateDaemonJvm`; re-apply the slim Linux URLs after
  any future regeneration.
- Root cause 1 is worth reporting upstream (sibling projects with colliding default `group:name` silently
  substituting under extra `-P` properties); the rename is the correct hygiene regardless.
