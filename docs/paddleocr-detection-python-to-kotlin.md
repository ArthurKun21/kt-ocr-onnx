# Porting PaddleOCR detection Python to Kotlin — a manual verification guide

How the text-detection pipeline in this repository maps to PaddleOCR's Python reference
implementation, and how to verify a port (or a model swap) by hand. It was written during the
PP-OCRv5 → PP-OCRv6 detection switch (see `plans/2026-09-04-switch-detection-to-ppocr-v6.md`).
The recognition counterpart lives in `docs/paddleocr-recognition-python-to-kotlin.md` — the two
pipelines share the method but differ in important conventions (channel order, dictionaries,
postprocessing), so each doc is self-contained.

## 1. Where the Python ground truth lives

Everything the models expect at inference time is defined in the [PaddleOCR repository](https://github.com/PaddlePaddle/PaddleOCR).
A local clone is the fastest way to read it (a checkout already exists at `/tmp/paddleocr`):

```bash
git clone --depth 1 https://github.com/PaddlePaddle/PaddleOCR /tmp/paddleocr
# pin to a specific revision when it matters, e.g. the PP-OCRv6 PR:
# git -C /tmp/paddleocr checkout b03f46425e8ff4442b268ce449e3eef758146cd4
```

Relevant layout:

| Path | Contents |
| --- | --- |
| `configs/det/<family>/<model>.yml` | Per-model config: postprocess params, normalization |
| `tools/infer/predict_det.py` + `tools/infer/utility.py` | Inference driver — the runtime preprocessing used in deployment (and the source of the det resize defaults) |
| `ppocr/data/imaug/operators.py` | `DetResizeForTest` implementation |
| `ppocr/postprocess/db_postprocess.py` | DB postprocess (binarize, contours, score, unclip) |

**Key rule:** the *inference contract* is defined by the per-model config yml **plus** the
`tools/infer/` driver. Training/eval configs and the PaddleX/python API defaults sometimes differ
from each other and from what `tools/infer` does — when in doubt, trace `tools/infer` because
that is the deployment path the ONNX models were exported for.

## 2. Tracing a detection model's inference contract — the five questions

For any new detection model, answer these five questions from Python, then check the Kotlin side
implements exactly that:

| # | Question | Where the answer lives (Python) | Where it is implemented (this repo) |
| --- | --- | --- | --- |
| 1 | Input resize rule | `tools/infer/utility.py` (`det_limit_side_len=960`, `det_limit_type="max"`) + `operators.py::DetResizeForTest` | `DetectionModel.detLimitSideLen` / `detRoundTo` + `PaddleOcrDetectionBase.preprocessImage` |
| 2 | Normalization | yml `NormalizeImage: mean/std` (ImageNet) | `DetectionModel.detMean` / `detStd` |
| 3 | Postprocess values | yml `PostProcess:` section (`thresh`, `box_thresh`, `max_candidates`, `unclip_ratio`) | `DetectionModel.detThresh` / `detBoxThresh` / `detMaxCandidates` / `detUnclipRatio` / `detMinSize` |
| 4 | Postprocess algorithm | yml `PostProcess: name: DBPostProcess` + `db_postprocess.py` | `PaddleOcrDetection.dbPostProcess` (jvm/android actuals) |
| 5 | ONNX graph I/O | export artifacts (verify with §6 step 2) | input name `"x"` in `PaddleOcrDetectionBase.runDetection` |

Unlike recognition, detection has **no dictionary** — the "dict" slot of the contract is the set
of DB postprocess values, which are per-model (see §4). They live as properties on
`DetectionModel` (`detection/model-core`) whose defaults mirror the Python `DBPostProcess`
class defaults; each bundled model overrides them with its yml values.

## 3. Preprocessing — `DetResizeForTest` + `NormalizeImage` → `preprocessImage`

Python reference (`ppocr/data/imaug/operators.py::DetResizeForTest.resize_image_type0`, as driven
by `tools/infer/utility.py` defaults `det_limit_side_len=960, det_limit_type="max"`):

```python
# limit the max side
if self.limit_type == "max":
    if max(h, w) > limit_side_len:
        ratio = float(limit_side_len) / max(h, w)   # (per-dimension branches for h/w)
    else:
        ratio = 1.0
resize_h = int(h * ratio)
resize_w = int(w * ratio)
...
resize_h = max(int(round(resize_h / 32) * 32), 32)
resize_w = max(int(round(resize_w / 32) * 32), 32)
img = cv2.resize(img, (int(resize_w), int(resize_h)))
```

Normalization (yml `NormalizeImage`, identical in the PP-OCRv5 and PP-OCRv6 det configs):

```yaml
- NormalizeImage:
    scale: 1./255.
    mean: [0.485, 0.456, 0.406]   # ImageNet, applied in RGB order
    std:  [0.229, 0.224, 0.225]
    order: hwc
```

Kotlin (`detection/detection-core/src/jvmCommonMain/.../PaddleOcrDetectionBase.preprocessImage`):

```kotlin
val limitSideLen = detectionModel.detLimitSideLen
val ratio = if (max(h, w) > limitSideLen) {
    limitSideLen.toFloat() / max(h, w).toFloat()
} else {
    1.0f
}

val roundTo = detectionModel.detRoundTo
val resizeH = max((h * ratio / roundTo).roundToInt() * roundTo, roundTo)
val resizeW = max((w * ratio / roundTo).roundToInt() * roundTo, roundTo)

val resizedImage = inputImage.resizeTo(resizeH, resizeW)
val floatImage = resizedImage.convertToFloat()

// Hoisted out of the per-pixel loop: interface getters allocate on each access.
val mean = detectionModel.detMean
val std = detectionModel.detStd
...
val normalized = (pixel[c].toFloat() / 255.0f - mean[c]) / std[c]
// channel-major write: c * resizeH * resizeW + y * resizeW + x  (NCHW)
```

Correspondence notes:

| Python | Kotlin | Note |
| --- | --- | --- |
| `ratio` from `limit_type="max"` | same branch on `detLimitSideLen` | Driver default 960 is the interface default; per-model override possible |
| `int(h * ratio)` then `round(resize_h / 32) * 32` | `(h * ratio / roundTo).roundToInt() * roundTo` | Kotlin rounds the scaled size directly; Python truncates to `int` first — can differ by one 32-step in rare edge cases |
| `cv2.resize(img, (resize_w, resize_h))` | `inputImage.resizeTo(resizeH, resizeW)` | Sizes are `(width, height)` on both sides — a classic swap bug |
| `(x/255 - mean) / std` in RGB order | same, `c` iterates RGB | The service converts crops to RGB before preprocessing — **opposite of recognition**, which stays BGR |
| `ToCHWImage` | explicit channel-major buffer index | `transpose(2,0,1)` equivalent |
| `max_side_limit=4000` clamp after resize | not implemented | Only relevant for very large images |
| (no padding) | no padding | Unlike recognition's `resize_norm_img`, det never pads |

## 4. Postprocessing — `DBPostProcess` → `dbPostProcess` with per-model constants

Python reference (`ppocr/postprocess/db_postprocess.py::DBPostProcess.__init__`):

```python
def __init__(self, thresh=0.3, box_thresh=0.7, max_candidates=1000,
             unclip_ratio=2.0, use_dilation=False, score_mode="fast", box_type="quad", **kwargs):
    ...
    self.min_size = 3
```

These class defaults are the default values of the `DetectionModel` properties. **Each bundled
model then carries its own config-yml values**, so swapping models swaps the tuning with them:

| Property (default = Python DBPostProcess) | PP-OCRv5_mobile_det.yml | PP-OCRv6_small_det.yml |
| --- | --- | --- |
| `detThresh` (0.3f) | 0.3 (inherit) | **0.2** |
| `detBoxThresh` (0.7f) | **0.6** | **0.45** |
| `detMaxCandidates` (1000) | 1000 (inherit) | **3000** |
| `detUnclipRatio` (2.0f) | **1.5** | **1.4** |
| `detMinSize` (3) | inherit | inherit |
| `detLimitSideLen` (960), `detRoundTo` (32) | inherit | inherit |
| `detMean`/`detStd` (ImageNet) | inherit | inherit |

The algorithm itself is platform-split Kotlin:

- `detection/detection-core/src/jvmMain/.../PaddleOcrDetection.kt::dbPostProcess` — JavaCPP OpenCV
  (`org.bytedeco.opencv.*`): binarize at `detThresh` → `findContours` (capped at `detMaxCandidates`)
  → `minAreaRect` → side check against `detMinSize` (and `detMinSize + 2` after re-fit) →
  `boxScoreFast` against `detBoxThresh` → `unclipPolygon` (Clipper2, distance =
  `area * detUnclipRatio / perimeter`) → scale back to original image coordinates.
- `androidMain/.../PaddleOcrDetection.kt::dbPostProcess` — same steps with `org.opencv.*`.
- Shared helpers (`getMiniBoxPoints`, `unclipPolygon`, polygon area/perimeter) live in
  `PaddleOcrDetectionBase`, matching `db_postprocess.py`'s `get_mini_boxes` / `unclip` /
  `box_score_fast`.

Reference: `ppocr/postprocess/db_postprocess.py`.

## 5. Gotchas checklist (detection)

1. **Channel order.** Detection loads BGR (`DecodeImage: img_mode: BGR`) but normalizes with
   ImageNet mean/std in **RGB** order — the service converts crops to RGB upstream. Recognition
   is the opposite (BGR end-to-end). Mixing these up silently degrades accuracy.
2. **Rounding.** Det rounds to the *nearest* 32 multiple with a floor of 32 (and Python truncates
   with `int()` before rounding); recognition uses `ceil` for its width. Don't reuse one
   pipeline's rounding in the other.
3. **Per-model tuning.** The DB thresholds differ per model generation — a swapped model brings
   its own `DetectionModel` values. If a custom model has no config, the Python class defaults
   (0.3 / 0.7 / 1000 / 2.0 / 3) apply, not the bundled models' values.
4. **ONNX I/O.** Input name `"x"` (paddle2onnx convention), dynamic H/W input (the Kotlin feeds
   arbitrary round-to-32 sizes), output is a probability map `(1, 1, H, W)`.
5. **`max_side_limit` guard.** Python clamps the resized image to 4000 after the ratio resize;
   Kotlin omits it (only relevant for very large images).
6. **Batch.** Always batch=1; no `valid_ratio`-style extras, no dilation (`use_dilation=False`).

## 6. Manual verification workflow

### Step 0 — Pin the Python revision

```bash
git clone --depth 1 https://github.com/PaddlePaddle/PaddleOCR /tmp/paddleocr
# For the PP-OCRv6 work, the relevant change is PR #18104:
#   gh pr diff 18104 --repo PaddlePaddle/PaddleOCR | less
```

### Step 1 — Fill the five-question table (§2) from the yml + driver

Read the model's yml (`configs/det/PP-OCRv6/PP-OCRv6_small_det.yml` etc.) and
`tools/infer/utility.py` + `predict_det.py`. Every `DetectionModel` property should trace back to
a yml line or a driver default.

### Step 2 — Inspect the ONNX graph

```python
import onnx
m = onnx.load("detection/model-base/src/commonMain/composeResources/files/PP-OCRv6_small_det.onnx")
print([(i.name, [d.dim_param or d.dim_value for d in i.type.tensor_type.shape.dim])
       for i in m.graph.input])
print([(o.name, [d.dim_param or d.dim_value for d in o.type.tensor_type.shape.dim])
       for o in m.graph.output])
# expect input  x : (1, 3, H, W) with dynamic H/W, output: (1, 1, H, W)
```

### Step 3 — Numeric dump comparison (preprocessing)

Replicate the Kotlin preprocessing and dump the tensor:

```python
# dump_det_input.py — replicates PaddleOcrDetectionBase.preprocessImage for batch=1
import cv2, numpy as np

LIMIT_SIDE_LEN, ROUND_TO = 960, 32                        # detLimitSideLen / detRoundTo
MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)  # RGB order
STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)   # RGB order

img = cv2.imread("image.png")                # BGR
img = img[:, :, ::-1]                        # the service converts crops to RGB upstream
h, w = img.shape[:2]
ratio = LIMIT_SIDE_LEN / max(h, w) if max(h, w) > LIMIT_SIDE_LEN else 1.0
resize_h = max(round(h * ratio / ROUND_TO) * ROUND_TO, ROUND_TO)
resize_w = max(round(w * ratio / ROUND_TO) * ROUND_TO, ROUND_TO)
resized = cv2.resize(img, (resize_w, resize_h)).astype("float32")
resized = (resized / 255.0 - MEAN) / STD
nchw = resized.transpose((2, 0, 1))[None]    # HWC -> CHW -> NCHW
nchw.tofile("det_input.bin")                 # raw float32, little-endian
print(resize_h, resize_w)
```

Then in a JVM test (or a scratch `main`), build the tensor via `preprocessImage` and compare
buffers element-wise (read `det_input.bin` as a little-endian `FloatBuffer`, walk both, track max
abs diff). A small non-zero diff is expected from resize rounding; anything above ~1e-3 means a
real mismatch (channel order, index math, normalization).

### Step 4 — End-to-end boxes

The DB postprocess involves contours and Clipper offsetting, which is impractical to mirror in a
short script — verify end-to-end instead:

- Run this repo's detection golden tests (`./gradlew :kt-ocr-onnx-detection:jvmTest`) against
  `kt-ocr-onnx/src/sharedTestAssets/ocr/` (asserts non-empty boxes, in-bounds coordinates, score
  > 0.6, and `boxes.txt` baselines when present).
- Optionally compare against the reference implementation:
  `paddleocr text_detection -i <image>` (PaddleX CLI) and compare box counts/coordinates within
  tolerance — remember the resize cap differs from Python's if you overrode `detLimitSideLen`.

### Step 5 — Wider regression

`./gradlew :kt-ocr-onnx:jvmTest` runs the full detect-and-recognize pipeline.

## 7. Worked example — PP-OCRv5 → PP-OCRv6 detection

What was actually checked for the v6 switch (PR #18104), in order:

1. **Diff review**: `predict_det.py` only gained the v6 model names; `DetResizeForTest`,
   `NormalizeImage` usage, and `db_postprocess.py` were untouched ⇒ no algorithm changes needed.
2. **Config**: `PP-OCRv6_small_det.yml` keeps the same det resize and ImageNet normalization;
   only `PostProcess` values differ (0.2 / 0.45 / 3000 / 1.4; `min_size` stays 3).
3. **Constants**: moved onto `DetectionModel` (defaults = Python `DBPostProcess` class defaults);
   `BaseDetectionModel` (v6) overrides four values, `V5BaseDetectionModel` overrides two — each
   model is self-describing, so swapping models swaps the tuning.
4. **Resources**: v6 ONNX in `detection/model-base`; the v5 mobile ONNX moved to the new
   `detection/model-v5-base`; `detection-core` no longer bundles resources.
5. **Golden tests**: detection JVM suites passed unchanged, which also validated the hardcoded
   input name `"x"`, the dynamic H/W input, and that the box-score assertions still held with the
   v6 model.

## 8. Remaining known differences

- **`DetResizeForTest` default upstream.** With no kwargs it defaults to min/736 in
  `ppocr/data/imaug/operators.py`, so the yml training/eval path and the `tools/infer` path
  genuinely differ upstream (min scales small images *up*; max caps large images *down*). This
  library follows the `tools/infer` driver (max/960).
- **`max_side_limit` guard.** Python clamps the resized image to 4000 after the ratio resize;
  Kotlin omits it (only relevant for very large images).
- **Rounding micro-differences.** Python truncates the scaled size with `int()` before the 32
  rounding; Kotlin rounds the scaled ratio directly — rare one-32-step differences are expected.

## 9. File map

| Contract piece | PaddleOCR Python | This repo (Kotlin) |
| --- | --- | --- |
| Model bytes | exported inference model directory | `detection/model-base` (v6), `detection/model-v5-base` — implement `detection/model-core`'s `DetectionModel` |
| Preprocessing | `tools/infer/predict_det.py` + `utility.py`, `ppocr/data/imaug/operators.py::DetResizeForTest`, yml `NormalizeImage` | `detection/detection-core/src/jvmCommonMain/.../PaddleOcrDetectionBase.kt::preprocessImage`; parameters are `DetectionModel` properties (`detLimitSideLen`, `detRoundTo`, `detMean`, `detStd`) |
| Postprocess | `ppocr/postprocess/db_postprocess.py` | `detection/detection-core/src/jvmMain/.../PaddleOcrDetection.kt::dbPostProcess` (+ `androidMain` actual); DB parameters are `DetectionModel` properties (`detThresh`, `detBoxThresh`, `detMaxCandidates`, `detUnclipRatio`, `detMinSize`) |
| Config constants | `configs/det/**` ymls | per-model `DetectionModel` property overrides |
| Golden tests | — | `kt-ocr-onnx/src/sharedTestAssets/ocr/` + `PaddleOcrDetectionServiceTestBase` |
