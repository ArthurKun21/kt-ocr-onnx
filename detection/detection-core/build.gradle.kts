import koo.buildlogic.MAVEN_PUBLISH_GROUP_ID

plugins {
    id("koo.library.kmp")
    id("koo.maven.publish")
    id("koo.opencv.jvm.platform")
}

kotlin {
    explicitApi()

    android {
        namespace = "com.github.arthurkun.koo.detection"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kt-ocr-onnx-core"))
            api(project(":detection:model-core"))
        }

        val jvmCommonMain = create("jvmCommonMain") {
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
