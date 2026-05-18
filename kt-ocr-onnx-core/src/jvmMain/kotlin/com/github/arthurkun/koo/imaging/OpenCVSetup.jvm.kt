package com.github.arthurkun.koo.imaging

public actual fun initializeOpenCvRuntime() {
    // No-op for JVM, as JavaCPP will load the native libraries automatically when needed.
}
