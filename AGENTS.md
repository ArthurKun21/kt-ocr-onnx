# AGENTS.md

## Build, lint, test

- JVM tests: `./gradlew :kt-ocr-onnx:jvmTest` (or `:kt-ocr-onnx-detection:jvmTest`,
  `:kt-ocr-onnx-recognition:jvmTest`).
- Single JVM test:
  `./gradlew :kt-ocr-onnx:jvmTest --tests 'com.github.arthurkun.koo.OcrPipelineTest.testWholeImageBoxUsesImageBounds'`.
- Android device tests: `./gradlew :kt-ocr-onnx:connectedAndroidDeviceTest`; host tests live under
  `androidHostTest`.
- Lint/format: `./gradlew spotlessCheck`; fix with `./gradlew spotlessApply`.
- After public API changes in ABI-validated modules run `./gradlew :kt-ocr-onnx:updateKotlinAbi` (
  same task pattern for detection/recognition API modules).
- JVM tests need native access for OpenCV/ONNX; `--enable-native-access=ALL-UNNAMED` is already
  configured where needed.

## Architecture

- Kotlin Multiplatform OCR library for Android + JVM using PaddleOCR ONNX models (PP-OCRv6
  detection and recognition) and OpenCV; no app UI or database layer.
- Public aggregator module `:kt-ocr-onnx` exposes `OcrApi`, `PaddleOcrService`, `JvmOcrApi`,
  `AndroidOcrApi`, results, and `OCRException` types.
- Split artifacts: `:kt-ocr-onnx-core` has shared models/imaging primitives;
  `:kt-ocr-onnx-detection` and `:kt-ocr-onnx-recognition` expose standalone APIs.
- Runtime internals live in `:detection:detection-core`, `:detection:model-core`,
  `:detection:model-base`, `:recognition:recognition-core`, `:recognition:model-core`, and
  `:recognition:model-base`.
- Models/resources: detection ONNX in `detection/model-base/src/commonMain/composeResources/files/`
  (PP-OCRv6 small); recognition ONNX + dict in
  `recognition/model-base/src/commonMain/composeResources/files/` (PP-OCRv6 small). Legacy
  PP-OCRv5 models live in `detection/model-v5-base` and `recognition/model-v5-base` /
  `recognition/model-v5-kr` (Korean). Detection parameters (DB postprocess, resize, ImageNet
  normalization) are per-model properties on `detection/model-core`'s `DetectionModel`; each
  bundled model carries its own config-yml values.
- Source sets use `commonMain`, `androidMain`, `jvmMain`, and shared `jvmCommonMain`; shared test
  image assets live in `kt-ocr-onnx/src/sharedTestAssets/`.
- Platform boundaries use `expect`/`actual`; `NativeMat`/`CvImage` wrap platform OpenCV and should
  remain internal implementation details unless intentionally promoted.

## Code style and conventions

- Follow `.editorconfig`: 4 spaces, UTF-8, final newline, trim trailing whitespace, Kotlin max line
  length 120.
- Kotlin uses `explicitApi()`; every public declaration needs explicit visibility/return types,
  otherwise prefer `internal`.
- Spotless/ktlint style is `intellij_idea`; no wildcard imports, trailing commas allowed, XML also
  trimmed/newline-normalized.
- Packages are rooted at `com.github.arthurkun.koo` (`.imaging`, `.recognition`, etc.); keep API
  packages stable.
- Prefer suspend APIs and structured resource cleanup (`try/finally`/`close`) around native
  images/sessions; map failures to the existing `OCRException` hierarchy.
