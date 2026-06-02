package com.github.arthurkun.koo

internal const val CHANNELS = 3

internal const val DET_MODEL_PATH = "files/base/PP-OCRv5_mobile_det.onnx"

internal const val DET_LIMIT_SIDE_LEN = 960
internal const val DET_ROUND_TO = 32

internal val DET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
internal val DET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

internal const val DET_THRESH = 0.3f
internal const val DET_BOX_THRESH = 0.6f
internal const val DET_MAX_CANDIDATES = 1000
internal const val DET_UNCLIP_RATIO = 1.5f
internal const val DET_MIN_SIZE = 3
