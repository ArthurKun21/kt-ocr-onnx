import koo.buildlogic.MAVEN_PUBLISH_GROUP_ID

plugins {
    id("koo.library.kmp")
    id("koo.compose")
    id("koo.maven.publish")
    id("koo.opencv.jvm.platform")
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
            }
        }
    }
}

compose.resources {
    packageOfResClass = "com.github.arthurkun.koo.detection.resources"
}

mavenPublishing {
    coordinates(
        groupId = MAVEN_PUBLISH_GROUP_ID,
        artifactId = "kt-ocr-onnx-detection-core",
        version = version.toString(),
    )

    pom {
        name.set("Kt OCR ONNX Detection Runtime")
        description.set("Internal text detection runtime used by kt-ocr-onnx artifacts.")
    }
}
