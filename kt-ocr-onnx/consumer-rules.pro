# Keep all ONNX Runtime classes (they are accessed via JNI)
# https://onnxruntime.ai/docs/build/android.html#build-android-archive-aar
-keep class ai.onnxruntime.** { *; }
