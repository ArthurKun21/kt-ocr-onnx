# Switch detection to PP-OCRv6 with swappable, self-describing `DetectionModel`

- Date: 2026-09-04
- Branch: `feat/switch-to-rec-v6`
- Related commits: `60cb038` (added the ppocr v6 detection models), `526f1b9` (switch to ppocr v6
  small for recognition)
- Guidance: PaddleOCR PR [#18104](https://github.com/PaddlePaddle/PaddleOCR/pull/18104)
- See also: `plans/2026-09-03-switch-recognition-to-ppocr-v6.md`,
  `docs/paddleocr-detection-python-to-kotlin.md`

## Background

The bundled detection model moves from PP-OCRv5 mobile (hardcoded resource path inside
`detection-core`) to PP-OCRv6 small (already placed in `detection/model-base`), and detection
gains the same model-swapping architecture recognition has (`DetectionModel` interface, cache
policies, per-request model parameters, legacy v5 module).

### Why there is no algorithm overhaul

PR #18104's only detection-code change is adding the v6 det model names to `predict_det.py`'s
supported list; `DetResizeForTest`, `NormalizeImage`, and `db_postprocess.py` are untouched.
`PP-OCRv6_small_det.yml` keeps the identical inference contract (dynamic det resize, ImageNet RGB
normalization, DB probability map output) — only the DB postprocess values differ per model.

### Constants design: per-model on `DetectionModel`

`DetectionModel` gains the DB/preprocessing parameters as interface properties whose default
getters mirror the Python `DBPostProcess.__init__` defaults (`thresh=0.3, box_thresh=0.7,
max_candidates=1000, unclip_ratio=2.0, min_size=3` — note the class defaults are *not* what the
v5 yml sets). Each bundled model carries its own yml values:

| Property (default = Python DBPostProcess) | V5 mobile (yml) | V6 small (yml) |
| --- | --- | --- |
| `detThresh` (0.3f) | 0.3 (inherit) | **0.2** |
| `detBoxThresh` (0.7f) | **0.6** | **0.45** |
| `detMaxCandidates` (1000) | 1000 (inherit) | **3000** |
| `detUnclipRatio` (2.0f) | **1.5** | **1.4** |
| `detMinSize` (3) | inherit | inherit |
| `detLimitSideLen` (960), `detRoundTo` (32) | inherit | inherit |
| `detMean`/`detStd` (ImageNet) | inherit | inherit |

Resize/norm defaults preserve current behavior exactly (`detLimitSideLen` = 960 matches both the
previous `detection-core` constant and the Python `tools/infer` driver default). Unlike
recognition (where v5/v6 share all constants), det models genuinely differ — per-model constants
make each model self-describing.

## Changes

### 1. `detection/model-v5-base` (new module, resource move)

- `git mv` of `PP-OCRv5_mobile_det.onnx` out of `detection-core` (its `composeResources` tree is
  removed); `build.gradle.kts` mirroring `detection/model-base` with namespace/package
  `com.github.arthurkun.koo.detection.v5.base`, artifactId `kt-ocr-onnx-detection-model-v5-base`.
- `V5BaseDetectionModel` (id `"pp-ocrv5-mobile-base"`) overrides `detBoxThresh = 0.6f` and
  `detUnclipRatio = 1.5f` per `PP-OCRv5_mobile_det.yml`; everything else matches the interface
  defaults.
- Included in `settings.gradle.kts`.

### 2. Constants onto `DetectionModel` + detection-core refactor

- `DetectionModel` (model-core): nine properties with default getters and KDoc citing the Python
  sources — `detThresh`, `detBoxThresh`, `detMaxCandidates`, `detUnclipRatio`, `detMinSize`,
  `detLimitSideLen`, `detRoundTo`, `detMean`, `detStd`.
- `BaseDetectionModel` (v6 small): overrides `detThresh = 0.2f`, `detBoxThresh = 0.45f`,
  `detMaxCandidates = 3000`, `detUnclipRatio = 1.4f`.
- `PaddleOcrDetectionBase`: ctor `(scope, protected val detectionModel: DetectionModel = BaseDetectionModel)`;
  loads bytes via `detectionModel.loadModelBytes()`; preprocessing reads
  `detLimitSideLen`/`detRoundTo` and hoists `detMean`/`detStd` into locals before the per-pixel
  loop (interface getters allocate per access); `unclipPolygon` uses `detUnclipRatio`; KDoc v6.
- `PaddleOcrDetection` (jvm/android actuals): ctor takes `detectionModel`;
  `dbPostProcess` reads `detectionModel.detThresh`/`.detMaxCandidates`/`.detMinSize`/`.detBoxThresh`.
- New `DetectionModelManager` (jvmCommonMain) mirroring `RecognitionModelManager`.
- `DetectionDefaults.kt` deleted (all nine constants move).

### 3. `kt-ocr-onnx-detection` API module

- Deps: `api(project(":detection:model-core"))` + `api(project(":detection:model-base"))`.
- `DetectionApi` overloads (ByteArray/Source/String/Path) gain
  `detectionModel: DetectionModel = BaseDetectionModel`; platform APIs (Mat/Bitmap/Uri) likewise.
- `PaddleOcrDetectionService` expect/actuals: ctor
  `(detectionModel, detectionModelCachePolicy)` (android prepends `platformContext`), delegating
  through `DetectionModelManager`.
- Test base gains `createPaddleOcrDetectionService(detectionModel, detectionModelCachePolicy)` and
  recognition-style swap tests with a `CountingDetectionModel`.

### 4. `kt-ocr-onnx` pipeline module

- Deps: `api(project(":detection:model-core"))` + `api(project(":detection:model-base"))`.
- `OcrApi.detectText(...)` overloads gain `detectionModel`;
  `detectAndRecognizeText(..., recognitionModel, detectionModel)` (detection param appended after
  the recognition param for source compatibility).
- `PaddleOcrService` expect/actuals thread both models; ctors append
  `(detectionModel, detectionModelCachePolicy)` after the recognition params; detection runs
  through `DetectionModelManager`. `OcrPipeline.kt` unchanged (model-agnostic lambdas).
- `PaddleOcrServiceTestBase.createPaddleOcrService` gains the two detection params; swap tests
  extended.

### 5. Cleanup / docs

- `kt-ocr-onnx/Defaults.kt`: dead `DET_*` block removed; dangling `[DET_ROUND_TO]` /
  `[DET_BOX_THRESH]` KDoc links fixed (the latter in `kt-ocr-onnx-core/.../DetectedResults.kt`).
- `kt-ocr-onnx-recognition/build.gradle.kts`: pom description "v5" → v6.
- `AGENTS.md` updated; the porting guide split into
  `docs/paddleocr-detection-python-to-kotlin.md` and
  `docs/paddleocr-recognition-python-to-kotlin.md`.

## Verification

1. `./gradlew :detection:model-v5-base:assemble :detection:model-base:assemble`
2. `./gradlew :kt-ocr-onnx-detection:jvmTest :kt-ocr-onnx:jvmTest :kt-ocr-onnx-recognition:jvmTest`
   — real v6 det inference (validates ONNX input name `"x"` and dynamic H/W). Det tests assert
   box score > 0.6; if the v6 score distribution trips that, cross-check against the Python
   reference before adjusting.
3. `./gradlew :kt-ocr-onnx-detection:updateKotlinAbi :kt-ocr-onnx:updateKotlinAbi` — required:
   `DetectionApi`/`OcrApi` signatures change; both modules have `abiValidation()`.
4. `./gradlew spotlessCheck`.
5. Android device tests need a device; shared test-base changes must compile.

## Out of scope

- No bundled v6 tiny/medium det modules (drop-in via custom `DetectionModel`).
- No behavior change to the det resize limit (`detLimitSideLen` keeps the previous 960 max-side cap).
