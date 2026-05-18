import koo.buildlogic.MAVEN_PUBLISH_GROUP_ID

plugins {
    id("koo.library.kmp")
    id("koo.maven.publish")
}

kotlin {
    explicitApi()

    android {
        namespace = "com.github.arthurkun.koo.recognition.runtime"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kt-ocr-onnx-core"))
            api(project(":recognition:model-core"))
        }

        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                compileOnly(libs.onnxruntime.jvm)
            }
        }

        androidMain {
            dependsOn(jvmCommonMain)
            dependencies {
                implementation(libs.onnxruntime.android)
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
        artifactId = "kt-ocr-onnx-recognition-core",
        version = version.toString(),
    )

    pom {
        name.set("Kt OCR ONNX Recognition Core")
        description.set("Internal text recognition runtime used by kt-ocr-onnx artifacts.")
    }
}
