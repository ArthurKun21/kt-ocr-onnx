plugins {
    id("koo.library.kmp")
}

kotlin {
    explicitApi()

    android {
        namespace = "com.github.arthurkun.koo.detection.api"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kt-ocr-onnx-core"))
            api(project(":detection"))
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
