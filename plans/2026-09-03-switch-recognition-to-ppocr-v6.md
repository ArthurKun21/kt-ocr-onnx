# Switch recognition to PP-OCRv6, restore v5 models as `model-v5-base` / `model-v5-kr`

- Date: 2026-09-03
- Branch: `feat/switch-to-rec-v6`
- Related commits: `43c0e3b` (replace model with v6 small version), `8ab639b` (deleted the model kr v5)
- Guidance: PaddleOCR PR [#18104](https://github.com/PaddlePaddle/PaddleOCR/pull/18104) ("[Feat] Support PP-OCRv6")

## Background

Commit `43c0e3b` already swapped the bundled recognition resources from PP-OCRv5 mobile to
PP-OCRv6 small (`PP-OCRv6_small_rec.onnx` + `ppocrv6_dict.txt`, 18,708 entries) and updated the
resource paths in `BaseRecognitionModel`. This plan covers the remaining alignment work and the
restoration of the two deleted/overwritten v5 model modules under new names.

### What PR #18104 actually changes

- `tools/infer/predict_rec.py`: only adds `PP-OCRv6_{tiny,small,medium}_rec` to the supported-model
  list — no preprocessing change.
- `ppocr/data/imaug/rec_img_aug.py` (RecResizeImg / `resize_norm_img`) and
  `ppocr/postprocess/rec_postprocess.py` (CTCLabelDecode) are untouched — v6 uses the identical
  inference pipeline as v5.
- `configs/rec/PP-OCRv6/PP-OCRv6_{small,tiny,medium}_rec.yml` all use
  `d2s_train_image_shape: [3, 48, 320]`, `RecResizeImg image_shape: [3, 48, 320]`,
  `use_space_char: true`, PostProcess `CTCLabelDecode`.
- New dicts: `ppocr/utils/dict/ppocrv6_dict.txt` (small/medium) and
  `ppocr/utils/dict/ppocrv6_tiny_dict.txt` (tiny only, ~49 languages, no Japanese).
- Python-side defaults moved to v6 (`PP-OCRv6_medium_rec` in `paddleocr/_models/text_recognition.py`;
  `PP-OCRv6` version maps to small det/rec models in `_pipelines/ocr.py`).

**Conclusion:** recognition-core needs no logic changes. The CTC convention is unchanged
(index 0 = blank, dict chars from index 1, space appended last because `use_space_char: true`),
the bundled 18,708-entry dict has only single-character lines, and the decoder is class-count
agnostic (argmax over the last output dim).

### Tiny/medium compatibility

- **PP-OCRv6_medium_rec**: same `ppocrv6_dict.txt`, same `[3,48,320]` input, same decode — zero
  code changes; drop-in via a custom `RecognitionModel`.
- **PP-OCRv6_tiny_rec**: same `[3,48,320]` input and decode, but pairs the ONNX with its own
  `ppocrv6_tiny_dict.txt` — zero code changes, just supply matching model + dict bytes through the
  existing `RecognitionModel` (`loadModelBytes()` / `loadDictionaryBytes()`).
- Caveat: all official exports use ONNX input name `x` (paddle2onnx convention); verify on any swap.

## Changes

### 1. Restore `recognition/model-v5-base` (old model-base, from `43c0e3b^` = `90f2db4`)

Extract verbatim via `git show <rev>:<path>`:

- `src/commonMain/composeResources/files/PP-OCRv5_mobile_rec.onnx` (16.5 MB)
- `src/commonMain/composeResources/files/ppocrv5_dict.txt`

Adapted files:

- `build.gradle.kts`: namespace `com.github.arthurkun.koo.recognition.v5.base`,
  `packageOfResClass = "com.github.arthurkun.koo.recognition.v5.base.resources"`, artifactId
  `kt-ocr-onnx-recognition-model-v5-base`, pom name "Kt OCR ONNX Model V5 Base".
- `V5BaseRecognitionModel.kt`: old `BaseRecognitionModel.kt` with package
  `com.github.arthurkun.koo.recognition.v5.base` and object renamed `V5BaseRecognitionModel`
  (the v6 `BaseRecognitionModel` keeps its name in `...recognition.base`). `id` stays
  `"pp-ocrv5-mobile-base"`; resource paths stay `files/PP-OCRv5_mobile_rec.onnx` /
  `files/ppocrv5_dict.txt`.

### 2. Restore `recognition/model-v5-kr` (old model-kr, from `8ab639b^` = `43c0e3b`)

Extract verbatim:

- `src/commonMain/composeResources/files/korean_PP-OCRv5_mobile_rec.onnx` (13.4 MB)
- `src/commonMain/composeResources/files/ppocrv5_korean_dict.txt` (11,945 lines)

Adapted files:

- `build.gradle.kts`: namespace/package `com.github.arthurkun.koo.recognition.v5.kr`,
  `packageOfResClass = "com.github.arthurkun.koo.recognition.v5.kr.resources"`, artifactId
  `kt-ocr-onnx-recognition-model-v5-kr`, pom name "Kt OCR ONNX Model V5 KR".
- `V5KrRecognitionModel.kt`: old `KrRecognitionModel.kt` with new package and object renamed
  `V5KrRecognitionModel` (consistent with the base module). `id` stays `"pp-ocrv5-mobile-kr"`.

Both modules remain standalone dependencies (opt-in via artifact), exactly like the old
`model-kr` — no aggregator wiring.

### 3. Wiring + v6 alignment

- `settings.gradle.kts`: include `:recognition:model-v5-base` and `:recognition:model-v5-kr`.
- `recognition/model-base/.../BaseRecognitionModel.kt` (v6): KDoc → "PaddleOCR v6 small
  recognition model bundled with the library."; `id` → `"pp-ocrv6-small-base"`.
- `recognition/recognition-core/.../PaddleOcrRecognition.kt` — KDoc only: v5 → v6 wording;
  PP-OCRv6 doc link `https://github.com/PaddlePaddle/PaddleOCR/blob/main/docs/version3.x/algorithm/PP-OCRv6/PP-OCRv6.md`
  + HF model card `https://huggingface.co/PaddlePaddle/PP-OCRv6_small_rec_onnx`; dict links →
  `ppocr/utils/dict/ppocrv6_dict.txt` on main. `resize_norm_img` / `CTCLabelDecode` references stay
  (upstream unchanged).
- `recognition/recognition-core/.../RecognitionImagePreprocessor.kt` (expect) — KDoc: v5 → v6 small.
- `kt-ocr-onnx/.../Defaults.kt` — comments for `TARGET_HEIGHT`/`TARGET_WIDTH` → PP-OCRv6.
  Detection constants stay v5.
- `README.md` — "PaddleOCR v5 Documentation" → "PaddleOCR v6 Documentation" (URL unchanged).
- `AGENTS.md` — mention `model-v5-base` / `model-v5-kr` resource modules.
- Hardcoded ONNX input name `"x"` stays; contingency: switch to `session.inputNames.first()` if
  jvmTest reports an unknown input name.

## Verification

1. `./gradlew :recognition:model-v5-base:assemble :recognition:model-v5-kr:assemble`
2. `./gradlew :kt-ocr-onnx-recognition:jvmTest :kt-ocr-onnx:jvmTest` — end-to-end v6 inference vs.
   `kt-ocr-onnx/src/sharedTestAssets/ocr/noble-phantasm-en/` baseline ("Gate of Skye" / "Lv");
   update `text.txt` only if v6's actual output differs.
3. `./gradlew :kt-ocr-onnx-detection:jvmTest` — detection unaffected.
4. `./gradlew spotlessCheck` (spotlessApply first if needed).
5. No `updateKotlinAbi` needed — no public API shape changes (new module, KDoc edits, `id` value).

## Out of scope

- Detection stays on `PP-OCRv5_mobile_det.onnx`.
- No bundled v6 tiny/medium modules (confirmed compatible; add later as `model-v6-tiny` etc. —
  only ONNX + dict files needed).
