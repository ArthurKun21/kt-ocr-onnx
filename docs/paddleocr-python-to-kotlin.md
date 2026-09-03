# Porting PaddleOCR Python to Kotlin — a manual verification guide

This document explains how the OCR pipeline in this repository maps to PaddleOCR's Python
reference implementation, and gives a step-by-step procedure to verify a port (or a model swap)
by hand. It was written while switching recognition from PP-OCRv5 to PP-OCRv6
(see `plans/2026-09-03-switch-recognition-to-ppocr-v6.md`), but it applies to any future port.

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
| `configs/<task>/<family>/<model>.yml` | Per-model config: input shape, dictionary, postprocess, normalization |
| `tools/infer/predict_rec.py`, `predict_det.py` | Inference drivers — the actual runtime preprocessing/decoding used in deployment |
| `ppocr/data/imaug/` | Transform implementations (`RecResizeImg`, `DetResizeForTest`, `NormalizeImage`, …) |
| `ppocr/postprocess/` | Decoders (`rec_postprocess.py::CTCLabelDecode`, `db_postprocess.py`) |
| `ppocr/utils/dict/` | Character dictionaries (one entry per line) |

**Key rule:** the *inference contract* is defined by the per-model config yml **plus** the
`tools/infer/` driver. Training configs and the PaddleX/python API defaults sometimes differ
from each other and from what `tools/infer` does — when in doubt, trace `tools/infer` because
that is the deployment path the ONNX models were exported for.

## 2. Tracing a model's inference contract — the five questions

For any new model, answer these five questions from Python, then check the Kotlin side implements
exactly that:

| # | Question | Where the answer lives (Python) | Where it is implemented (this repo) |
| --- | --- | --- | --- |
| 1 | Input H/W and resize rule | yml `Eval: RecResizeImg: image_shape` (or `d2s_train_image_shape`) + `tools/infer/predict_rec.py::resize_norm_img` | `RecognitionDefaults.kt` (`TARGET_HEIGHT`, `TARGET_WIDTH`), `RecognitionImagePreprocessor` actuals |
| 2 | Normalization | rec: hard-coded `(x/255 - 0.5) / 0.5` in `resize_norm_img`; det: yml `NormalizeImage: mean/std` | rec preprocessor; `detMean`/`detStd` on `DetectionModel` |
| 3 | Dictionary + space char | yml `character_dict_path`, `use_space_char` | `model-base`/`model-v5-*` resource consts + `parseDictionary` |
| 4 | Postprocess algorithm | yml `PostProcess: name` (rec: `CTCLabelDecode`) | `PaddleOcrRecognition.ctcDecode` |
| 5 | ONNX graph I/O | export artifacts (verify with the snippet in §7 step 2) | input name `"x"` in `PaddleOcrRecognition.runInference` |

## 3. Recognition preprocessing — `resize_norm_img` → `preprocessRecognitionImage`

Python reference (`tools/infer/predict_rec.py`, the branch used by CTC rec models, lines 236–261):

```python
assert imgC == img.shape[2]
imgW = int((imgH * max_wh_ratio))          # batch-wide width (dynamic, capped by ONNX shape)
h, w = img.shape[:2]
ratio = w / float(h)
if math.ceil(imgH * ratio) > imgW:
    resized_w = imgW
else:
    resized_w = int(math.ceil(imgH * ratio))
resized_image = cv2.resize(img, (resized_w, imgH))     # BGR, cv2 default INTER_LINEAR
resized_image = resized_image.astype("float32")
resized_image = resized_image.transpose((2, 0, 1)) / 255
resized_image -= 0.5
resized_image /= 0.5
padding_im = np.zeros((imgC, imgH, imgW), dtype=np.float32)
padding_im[:, :, 0:resized_w] = resized_image
return padding_im
```

Kotlin implementation (`recognition/recognition-core/src/jvmMain/.../RecognitionImagePreprocessor.kt`,
with the Android actual mirroring it via `org.opencv.*`):

```kotlin
val resizedW = recognitionResizeWidth(inputImage.width, inputImage.height) // min(ceil(48 * w/h), 320)
resize(nativeImage.mat, resizedImage, Size(resizedW, TARGET_HEIGHT))
resizedImage.convertTo(floatImage, CV_32FC3)
val buffer = FloatBuffer.allocate(1 * CHANNELS * TARGET_HEIGHT * TARGET_WIDTH)
floatImage.createIndexer<FloatIndexer>().use { indexer ->
    for (c in 0 until CHANNELS)
        for (y in 0 until TARGET_HEIGHT)
            for (x in 0 until resizedW) {
                val pixel = indexer.get(y.toLong(), x.toLong(), c.toLong())
                buffer.put(c * TARGET_HEIGHT * TARGET_WIDTH + y * TARGET_WIDTH + x,
                           (pixel / 255.0f - 0.5f) / 0.5f)
            }
}
OnnxTensor.createTensor(env, buffer,
    longArrayOf(1, CHANNELS.toLong(), TARGET_HEIGHT.toLong(), TARGET_WIDTH.toLong()))
```

Line-by-line correspondence:

| Python | Kotlin | Note |
| --- | --- | --- |
| `imgW = int(imgH * max_wh_ratio)` | fixed `TARGET_WIDTH = 320` | Python widens `imgW` per batch (widest image); Kotlin always runs batch=1 with the 320-wide tensor. `resize_norm_img` also honors a static ONNX width if the graph has one. |
| `resized_w = int(math.ceil(imgH * ratio))`, capped at `imgW` | `min(ceil(TARGET_HEIGHT * ratio), TARGET_WIDTH)` | Equivalent for batch=1. Note it's `ceil`, not `round`. |
| `cv2.resize(img, (resized_w, imgH))` | `resize(mat, resizedImage, Size(resizedW, TARGET_HEIGHT))` | Same default interpolation (bilinear). OpenCV sizes are `(width, height)` on both sides — a classic swap bug. |
| `transpose((2,0,1)) / 255 - 0.5 / 0.5` | loop writes `c*H*W + y*W + x` | `transpose(2,0,1)` turns HWC BGR into CHW; the explicit loop writes channel-major, which is the same thing. The indexer is `(y, x, channel)` because OpenCV Mats stay HWC. |
| `np.zeros((imgC, imgH, imgW))` then copy region | `FloatBuffer.allocate(...)` (zero-filled) then writes only `x < resizedW` | Padding is **zeros in normalized space** (i.e. gray 0.5 in raw pixel space), matching Python exactly. |
| BGR everywhere (`DecodeImage: img_mode: BGR`) | OpenCV Mat is BGR, channels read in order | The rec models are trained on BGR — **do not** convert to RGB here (see §6). |

## 4. Recognition postprocessing — `CTCLabelDecode` → `parseDictionary` + `ctcDecode`

### 4.1 Dictionary construction

Python (`ppocr/postprocess/rec_postprocess.py`, lines 36–51 and 230–232):

```python
with open(character_dict_path, "rb") as fin:
    lines = fin.readlines()
    for line in lines:
        line = line.decode("utf-8").strip("\n").strip("\r\n")
        self.character_str.append(line)
if use_space_char:
    self.character_str.append(" ")            # space appended BEFORE special char
dict_character = self.add_special_char(dict_character)
# CTCLabelDecode.add_special_char:
dict_character = ["blank"] + dict_character  # blank PREPENDED
```

Resulting index layout (this is the single most important convention):

```text
index 0        -> "blank" (CTC blank, ignored token)
index 1..N     -> dictionary lines, in file order
index N+1      -> " " (only when use_space_char=true; class count = N + 2)
```

Kotlin (`PaddleOcrRecognition.parseDictionary`) does the same: `charDict[0] = "blank"`, dict
lines from index 1, one trailing `" "`. Two deliberate divergences to keep in mind:

- Kotlin skips empty lines (`if (char.isNotEmpty())`); Python appends them as entries. Harmless
  for the official dicts (verified: 0 empty lines), but a dict with a blank line would shift
  every subsequent index in Python while Kotlin stays put.
- Kotlin only trims `\n`/`\r` from the end; Python `strip()` also removes leading whitespace.

### 4.2 Greedy CTC decode

Python (`CTCLabelDecode.__call__` + inherited `decode`, `get_ignored_tokens`):

```python
preds_idx  = preds.argmax(axis=2)
preds_prob = preds.max(axis=2)
# decode(..., is_remove_duplicate=True):
selection[1:] = text_index[1:] != text_index[:-1]   # drop immediate repeats
selection &= text_index != ignored_token            # ignored_tokens -> [0] (blank)
char_list  = [self.character[i] for i in ...[selection]]
conf_list  = text_prob[...][selection]              # confidences of KEPT steps only
# result: ("".join(char_list), mean(conf_list)); empty conf -> [0]
```

Kotlin (`PaddleOcrRecognition.ctcDecode`):

```kotlin
var maxIdx = ...; var maxVal = timestep[0]           // argmax over the class dimension
if (maxIdx != prevIdx && maxIdx != 0) {              // ≡ not a repeat AND not blank
    dictionary[maxIdx]?.let { charList.add(it); confidences.add(maxVal) }
}
prevIdx = maxIdx
val score = if (confidences.isEmpty()) 0f else confidences.average().toFloat()
```

`maxIdx != prevIdx && maxIdx != 0` is equivalent to Python's two-step selection mask: a kept
index must differ from the previous timestep's index and not be the blank. The mean confidence
is computed over kept characters only on both sides. Unknown indices are silently dropped by the
`dictionary[maxIdx]?.let` null-safety, which cannot happen when the dict size matches the model's
class count (`N + 2`) — a mismatch here is itself a bug signal.

## 5. Detection — a different flavor of the same exercise

Detection differs from recognition in two important ways and is worth reading as the
"other" template:

- **Resize** (`ppocr/data/imaug/operators.py::DetResizeForTest`): the inference driver
  (`tools/infer/utility.py`) defaults to `det_limit_side_len=960, det_limit_type="max"` — cap the
  **max** side at 960, then round both sides to the nearest multiple of 32 with a floor of 32
  (`max(int(round(side / 32) * 32), 32)`). Python also guards `max_side_limit=4000` after the
  ratio resize; Kotlin (`PaddleOcrDetectionBase.preprocessImage`) omits that guard (only relevant
  for very large images). The cap value comes from `DetectionModel.detLimitSideLen` (default 960,
  mirroring the Python driver default).
- **Normalization** (`NormalizeImage` in the det yml): ImageNet mean/std
  `(x/255 - [0.485, 0.456, 0.406]) / [0.229, 0.224, 0.225]` applied in **RGB** order, even though
  `DecodeImage` loads BGR. This is the opposite of recognition, which keeps BGR throughout.
  The Kotlin service converts crops to RGB upstream and `preprocessImage` comments this explicitly.
- **DB postprocess** (`ppocr/postprocess/db_postprocess.py`): threshold → binary map → contours →
  `box_score_fast` → unclip (Clipper offset) → rescale. Kotlin: `dbPostProcess` in
  `detection-core` (`jvmMain` uses JavaCPP OpenCV, `androidMain` uses `org.opencv.*`). The DB
  parameters are per-model properties on `DetectionModel` (`detThresh`, `detBoxThresh`,
  `detMaxCandidates`, `detUnclipRatio`, `detMinSize`) whose defaults mirror
  `DBPostProcess.__init__`; each bundled model overrides them with its config-yml values
  (PP-OCRv6 small: 0.2 / 0.45 / 3000 / 1.4; PP-OCRv5 mobile: 0.3 / 0.6 / 1000 / 1.5).

## 6. Gotchas checklist

1. **Channel order.** Recognition: BGR end-to-end (matches training). Detection: BGR loaded, RGB
   normalized. Mixing these up silently degrades accuracy — nothing crashes.
2. **Padding in normalized space.** Padding value is `0.0` *after* `(x/255 - 0.5)/0.5`, i.e. gray
   127.5 in raw pixels. Padding with raw-pixel zeros would be a different (wrong) gray.
3. **`ceil` vs `round`.** `resize_norm_img` uses `math.ceil` for the resized width; det rounds to
   the *nearest* 32 multiple (floor 32), not up.
4. **CTC offsets.** Blank at index 0, dict from 1, space last. `use_space_char` changes the class
   count (`N + 1` vs `N + 2`) — the dict file, the model's output dimension, and the decoder must
   agree.
5. **Fixed vs dynamic width.** Python can widen the tensor per batch (`max_wh_ratio`); this repo
   always runs batch=1 at 320 wide. Fine for correctness, just not bit-identical to a Python run
   that used a wider batch.
6. **ONNX input name.** The Kotlin session call hardcodes `"x"` (the paddle2onnx convention for
   PaddleOCR exports). Verify per model (§7 step 2).
7. **OpenCV size order** is `(width, height)` everywhere; the Mat indexer is `(y, x, channel)`.
8. **Intentional simplifications** in this port (documented, not bugs): no `valid_ratio`, no
   `return_word_box`, no Arabic `pred_reverse`, no NRTR/attention decoders — greedy CTC only.

## 7. Manual verification workflow

### Step 0 — Pin the Python revision

```bash
git clone --depth 1 https://github.com/PaddlePaddle/PaddleOCR /tmp/paddleocr
# For the PP-OCRv6 work, the relevant change is PR #18104:
#   gh pr diff 18104 --repo PaddlePaddle/PaddleOCR | less
```

### Step 1 — Fill the five-question table (§2) from the yml + driver

Read the model's yml (`configs/rec/PP-OCRv6/PP-OCRv6_small_rec.yml` etc.) and the matching
`tools/infer/predict_*.py` function. Every constant in `RecognitionDefaults.kt` /
`DetectionDefaults.kt` / `Defaults.kt` should trace back to a yml line or a driver default.

### Step 2 — Inspect the ONNX graph

```python
import onnx
m = onnx.load("recognition/model-base/src/commonMain/composeResources/files/PP-OCRv6_small_rec.onnx")
print([(i.name, [d.dim_param or d.dim_value for d in i.type.tensor_type.shape.dim])
       for i in m.graph.input])
print([(o.name, [d.dim_param or d.dim_value for d in o.type.tensor_type.shape.dim])
       for o in m.graph.output])
# expect input  x : (1, 3, 48, 320) with dynamic W, output: (1, T, N+2)
```

Also sanity-check the dictionary against the class count:

```bash
wc -l ppocrv6_dict.txt          # N dict entries; class count must be N + 2
grep -cE '^$' ppocrv6_dict.txt  # must be 0 (see §4.1 empty-line divergence)
grep -nE '^(..)' ppocrv6_dict.txt | head  # multi-char entries, if any
```

### Step 3 — Numeric dump comparison (preprocessing)

Run PaddleOCR's own math on a test image and dump the tensor:

```python
# dump_rec_input.py — replicates tools/infer/predict_rec.py::resize_norm_img for batch=1
import cv2, math, numpy as np
imgC, imgH, imgW = 3, 48, 320
img = cv2.imread("image.png")                       # BGR
h, w = img.shape[:2]
resized_w = min(int(math.ceil(imgH * (w / float(h)))), imgW)
resized = cv2.resize(img, (resized_w, imgH)).astype("float32")
resized = resized.transpose((2, 0, 1)) / 255
resized -= 0.5
resized /= 0.5
pad = np.zeros((imgC, imgH, imgW), dtype=np.float32)
pad[:, :, 0:resized_w] = resized
pad.tofile("rec_input.bin")                         # raw float32, little-endian
print("resized_w =", resized_w)
```

Then in a JVM test (or a scratch `main`), build the tensor with
`preprocessRecognitionImage(...)` and compare buffers element-wise:

```kotlin
val expected = java.nio.ByteBuffer.wrap(java.nio.file.Files.readAllBytes(Path.of("rec_input.bin")))
    .order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
val actual = preprocessRecognitionImage(image, OrtEnvironment.getEnvironment()) // OnnxTensor
var maxDiff = 0f
while (expected.hasRemaining()) {
    maxDiff = maxOf(maxDiff, abs(expected.get() - actual.floatBuffer.get()))
}
check(maxDiff < 1e-4f) { "max abs diff $maxDiff" }
```

A small non-zero diff is expected from `cv2.resize` vs OpenCV's Java `resize` (rounding in the
interpolation kernel); anything above ~1e-3 means a real mismatch (channel order, index math,
normalization).

### Step 4 — End-to-end decode comparison

Feed the same image through ONNX Runtime in Python and mirror the Kotlin decode exactly:

```python
import onnxruntime as ort
sess = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])
logits = sess.run(None, {"x": pad[None]})[0]        # (1, T, N+2)
idx, prob = logits.argmax(axis=2)[0], logits.max(axis=2)[0]
chars = ["blank"] + [l.rstrip("\n") for l in open(dict_path, encoding="utf-8")] + [" "]
out, prev, confs = [], -1, []
for i, p in zip(idx, prob):
    if i != prev and i != 0:
        out.append(chars[i]); confs.append(float(p))
    prev = i
print("".join(out), sum(confs) / max(len(confs), 1))
```

The text and score should match what `PaddleOcrRecognition.detectText` returns (score within
float tolerance).

### Step 5 — Run this repo's golden tests

`kt-ocr-onnx/src/sharedTestAssets/ocr/<case>/` holds test images with `text.txt` baselines; the
JVM suites run the full pipeline against them:

```bash
./gradlew :kt-ocr-onnx-recognition:jvmTest :kt-ocr-onnx:jvmTest
```

If a model swap changes recognized text, update the baselines **after** confirming with step 4
that the new output is actually correct — the baselines encode expected model behavior, not truth.

## 8. Worked example — PP-OCRv5 → PP-OCRv6

What was actually checked for the v6 switch (PR #18104), in order:

1. **Diff review**: `predict_rec.py` only gained model names; `rec_img_aug.py` and
   `rec_postprocess.py` untouched ⇒ no preprocessing/decoding changes needed.
2. **Config**: all three v6 rec ymls use `[3, 48, 320]`, `CTCLabelDecode`, `use_space_char: true`,
   so `TARGET_HEIGHT`/`TARGET_WIDTH` and the decoder stay as-is. Medium reuses
   `ppocrv6_dict.txt`; tiny needs its own `ppocrv6_tiny_dict.txt` (pair model+dict via a custom
   `RecognitionModel`).
3. **Dictionary**: 18,708 single-char lines, 0 empty ⇒ existing `parseDictionary` conventions hold.
4. **Resources + identity**: swap ONNX/dict files, point `BaseRecognitionModel` at them, update
   `id` and KDoc/docs (no ABI shape change).
5. **Golden tests**: recognition + pipeline JVM suites passed unchanged, which also validated the
   hardcoded input name `"x"`.

## 9. Known discrepancies found while writing this guide

**Resolved (2026-09-04, PP-OCRv6 detection switch):** the aggregator module's dead
`kt-ocr-onnx/Defaults.kt` copy of the detection constants claimed `DET_LIMIT_SIDE_LEN = 736` with
`limit_type="min"` semantics, while the real runtime value in `detection-core` was 960 with
max-side semantics — matching the Python driver. The dead copy is deleted, and the resize cap is
now the per-model `DetectionModel.detLimitSideLen` property (default 960, max-side).

Remaining known differences:

- **DetResizeForTest default upstream.** `DetResizeForTest` with no kwargs defaults to
  min/736 in `ppocr/data/imaug/operators.py`, so the yml training/eval path and the
  `tools/infer` path genuinely differ upstream too (min scales small images *up*; max caps large
  images *down*).
- **`max_side_limit` guard.** Python clamps the resized image to 4000 after the ratio resize;
  Kotlin omits that guard (only relevant for very large images).

## 10. File map

| Contract piece | PaddleOCR Python | This repo (Kotlin) |
| --- | --- | --- |
| Rec preprocessing | `tools/infer/predict_rec.py::TextRecognizer.resize_norm_img` | `recognition/recognition-core/src/jvmCommonMain/.../RecognitionImagePreprocessor.kt` (expect) + `jvmMain` / `androidMain` actuals; constants in `recognition-core/src/commonMain/.../RecognitionDefaults.kt` |
| Rec decode | `ppocr/postprocess/rec_postprocess.py::CTCLabelDecode` (+ `BaseRecLabelDecode`) | `recognition/recognition-core/src/jvmCommonMain/.../PaddleOcrRecognition.kt::parseDictionary` / `ctcDecode` |
| Model + dict bytes | exported inference model directory | detection: `detection/model-base` (v6), `detection/model-v5-base` — implement `detection/model-core`'s `DetectionModel`; recognition: `recognition/model-base` (v6), `recognition/model-v5-base`, `recognition/model-v5-kr` (Korean) |
| Det preprocessing | `tools/infer/predict_det.py`, `ppocr/data/imaug/operators.py::DetResizeForTest`, yml `NormalizeImage` | `detection/detection-core/src/jvmCommonMain/.../PaddleOcrDetectionBase.kt::preprocessImage`; resize/norm parameters are `DetectionModel` properties (`detLimitSideLen`, `detRoundTo`, `detMean`, `detStd`) |
| Det postprocess | `ppocr/postprocess/db_postprocess.py` | `detection/detection-core/src/jvmMain/.../PaddleOcrDetection.kt::dbPostProcess` (+ `androidMain` actual); DB parameters are `DetectionModel` properties (`detThresh`, `detBoxThresh`, `detMaxCandidates`, `detUnclipRatio`, `detMinSize`) |
| Config constants | `configs/rec/**`, `configs/det/**` ymls | recognition: `RecognitionDefaults.kt` + `kt-ocr-onnx/Defaults.kt`; detection: per-model `DetectionModel` property overrides |
| Golden tests | — | `kt-ocr-onnx/src/sharedTestAssets/ocr/` + `PaddleOcrServiceTestBase` / `PaddleOcrRecognitionServiceTestBase` |
