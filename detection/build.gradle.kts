plugins {
    id("koo.library.kmp")
    id("koo.compose")
}

kotlin {
    explicitApi()

    android {
        namespace = "com.github.arthurkun.koo.detection"
        androidResources.enable = true
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kt-ocr-onnx-core"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.resources)
        }

        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                compileOnly(libs.onnxruntime.jvm)
                implementation(libs.clipper2.java)
            }
        }

        androidMain {
            dependsOn(jvmCommonMain)
            dependencies {
                implementation(libs.onnxruntime.android)
                implementation(libs.opencv.android)
            }
        }

        jvmMain {
            dependsOn(jvmCommonMain)
            dependencies {
                implementation(libs.onnxruntime.jvm)
                implementation(libs.opencv.jvm)
            }
        }
    }
}

compose.resources {
    packageOfResClass = "com.github.arthurkun.koo.detection.resources"
}
