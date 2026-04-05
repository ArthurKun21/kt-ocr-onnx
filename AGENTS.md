# AGENTS.md

## Build & Test

- JVM tests: `./gradlew :kt-ocr-onnx:jvmTest`
- Android device tests: `./gradlew :kt-ocr-onnx:connectedAndroidDeviceTest`
- Lint/format: `./gradlew spotlessApply` (check: `./gradlew spotlessCheck`)
- API dump (after public API changes): `./gradlew :kt-ocr-onnx:apiDump`
- JVM tests require `--enable-native-access=ALL-UNNAMED` JVM arg (already configured in `build.gradle.kts`).

## Architecture

- Kotlin Multiplatform library (Android + JVM) for OCR using PaddleOCR v5 ONNX models.
- Uses two models: text detection (`PP-OCRv5_mobile_det.onnx`) and text recognition (`PP-OCRv5_mobile_rec.onnx`), bundled via Compose Resources in `src/commonMain/composeResources/files/base/`.
- Single module `:kt-ocr-onnx` with source sets: `commonMain`, `androidMain`, `jvmMain`, `jvmCommonMain`, `commonTest`, `jvmTest`, `androidDeviceTest`, `androidHostTest`.
- Shared test assets in `src/sharedTestAssets/` (shared between `jvmTest` and `androidDeviceTest`).
- Public API surface:
  - `OcrApi` — common interface with `suspend fun detectText(ByteArray): List<DetectedResults>`, `suspend fun recognizeText(ByteArray): RecognitionResult`, `suspend fun detectAndRecognizeText(ByteArray): List<OcrResult>`. Also has overloads for `Source`, `String` (path), and `Path`.
  - `PaddleOcrService(platformContext: Any? = null)` — `expect`/`actual` class implementing `OcrApi`. On Android, pass a `Context`; on JVM, pass `null` or omit.
  - `JvmOcrApi` — JVM-specific interface extending `OcrApi` with bytedeco `Mat` overloads.
  - `AndroidOcrApi` — Android-specific interface extending `OcrApi` with `Bitmap`, `Uri`, and OpenCV `Mat` overloads.
  - Data classes: `DetectedResults` (bounding quadrilateral + score), `RecognitionResult` (text + score), `OcrResult` (box + text + score), `BoxPoint` (x, y).
  - `OCRException` / `OCRReason` — error types for initialization and loading failures.
  - `CvImage` and `NativeMat` are **internal** — not part of the public API.
- Internal implementation: `PaddleOcrDetectionBase` (abstract, in `jvmCommonMain`) with platform-specific `PaddleOcrDetection` subclasses; `PaddleOcrRecognition` (in `jvmCommonMain`); `NativeMat` (`expect`/`actual`) wraps OpenCV Mat.
- Key deps: ONNX Runtime (android/jvm), OpenCV (`org.opencv:opencv` on Android, `org.bytedeco:opencv-platform` on JVM), Compose Resources for model files, kotlinx-coroutines, Clipper2-java for polygon operations.
- Binary compatibility tracked via `kotlinx.binary-compatibility-validator` (JVM target only; Android-specific APIs like `AndroidOcrApi` are not covered by BCV since they depend on Android SDK types unavailable in the JVM compilation).

## Code Style

- Kotlin with `explicitApi()` — all public declarations need explicit visibility modifiers. Use `internal` for non-API symbols.
- ktlint via Spotless; style: `intellij_idea`. Max line length: 120. Indent: 4 spaces. No wildcard imports. Trailing commas allowed.
- Package: `com.github.arthurkun.koo` (imaging: `com.github.arthurkun.koo.imaging`).
- Use `expect`/`actual` for platform-specific code. Prefer `suspend` functions for async work.
