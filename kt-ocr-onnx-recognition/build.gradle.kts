plugins {
    id("koo.library.kmp")
    id("koo.library.kmp.tests")
}

kotlin {
    explicitApi()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }

    android {
        namespace = "com.github.arthurkun.koo.recognition.api"

        optimization {
            consumerKeepRules.file("consumer-rules.pro")
        }
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

        val sharedTestAssetsDir = "../kt-ocr-onnx/src/sharedTestAssets"

        jvmTest {
            resources.srcDir(sharedTestAssetsDir)
        }

        getByName("androidDeviceTest") {
            resources.srcDir(sharedTestAssetsDir)
        }
    }
}
