plugins {
    id("koo.library.kmp")
}

kotlin {
    explicitApi()

    android {
        namespace = "com.github.arthurkun.koo.recognition.core"
    }
}
