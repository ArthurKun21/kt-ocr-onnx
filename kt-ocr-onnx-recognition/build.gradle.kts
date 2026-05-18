plugins {
    id("koo.library.kmp")
}

kotlin {
    explicitApi()

    android {
        namespace = "com.github.arthurkun.koo.recognition.api"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kt-ocr-onnx-core"))
            api(project(":recognition:recognition-core"))
            api(project(":recognition:model-core"))
            api(project(":recognition:model-base"))
            api(libs.kotlinx.io.core)
        }

        androidMain.dependencies {
            implementation(libs.opencv.android)
        }

        jvmMain.dependencies {
            implementation(libs.opencv.jvm)
        }
    }
}
