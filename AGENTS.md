# AGENTS.md

## Build & Test

- JVM tests: `./gradlew :kt-ocr-onnx:jvmTest`
- Android device tests: `./gradlew :kt-ocr-onnx:connectedAndroidTest`
- Lint/format: `./gradlew spotlessApply` (check: `./gradlew spotlessCheck`)
- JVM tests require `--enable-native-access=ALL-UNNAMED` JVM arg (already configured in `build.gradle.kts`).

## Architecture

- Kotlin Multiplatform library (Android + JVM) for OCR using PaddleOCR v5 ONNX models (recognition only, `PP-OCRv5_mobile_rec.onnx`).
- Single module `:kt-ocr-onnx` with source sets: `commonMain`, `androidMain`, `jvmMain`, `jvmCommonMain`, `commonTest`, `jvmTest`, `androidDeviceTest`, `androidHostTest`.
- Shared test assets in `src/sharedTestAssets/` (shared between `jvmTest` and `androidDeviceTest`).
- Public API: `OcrApi` (`suspend fun detectText(CvImage): String`), `CvImage` (factory `CvImage.fromByteArray()`), `OCRException`/`OCRReason`. Android-only: `AndroidOcrApi` with `detectText(Bitmap)`.
- Internal implementation: `PaddleOcrService` (`expect`/`actual`) implements `OcrApi`; `NativeMat` (`expect`/`actual`) implements `CvImage`.
- Key deps: ONNX Runtime (android/jvm), OpenCV (`org.opencv:opencv` on Android, `org.bytedeco:opencv-platform` on JVM), Compose Resources for model files, kotlinx-coroutines.

## Code Style

- Kotlin with `explicitApi()` — all public declarations need explicit visibility modifiers. Use `internal` for non-API symbols.
- ktlint via Spotless; style: `intellij_idea`. Max line length: 120. Indent: 4 spaces. No wildcard imports. Trailing commas allowed.
- Package: `com.github.arthurkun.koo` (imaging: `com.github.arthurkun.koo.imaging`).
- Use `expect`/`actual` for platform-specific code. Prefer `suspend` functions for async work.
