import com.android.build.gradle.tasks.MergeSourceSetFolders
import koo.buildlogic.MAVEN_PUBLISH_GROUP_ID

val sharedTestAssetsDir = "../kt-ocr-onnx/src/sharedTestAssets"

plugins {
    id("koo.library.kmp")
    id("koo.library.kmp.tests")
    id("koo.maven.publish")
}

kotlin {
    explicitApi()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }

    android {
        namespace = "com.github.arthurkun.koo.detection.api"

        optimization {
            consumerKeepRules.file("consumer-rules.pro")
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kt-ocr-onnx-core"))
            api(project(":detection:detection-core"))
            api(libs.kotlinx.io.core)
        }

        androidMain.dependencies {
            implementation(libs.opencv.android)
        }

        jvmMain.dependencies {
            implementation(libs.opencv.jvm)
        }

        jvmTest {
            resources.srcDir(sharedTestAssetsDir)
        }

        getByName("androidDeviceTest") {
            resources.srcDir(sharedTestAssetsDir)
        }
    }
}

tasks.withType<MergeSourceSetFolders>().matching { it.name == "mergeAndroidDeviceTestAssets" }.configureEach {
    sourceFolderInputs.from(sharedTestAssetsDir)

    doLast {
        project.copy {
            from(sharedTestAssetsDir)
            into(outputDir)
        }
    }
}

mavenPublishing {
    coordinates(
        groupId = MAVEN_PUBLISH_GROUP_ID,
        artifactId = "kt-ocr-onnx-detection",
        version = version.toString(),
    )

    pom {
        name.set("Kt OCR ONNX Detection")
        description.set("Text detection API for kt-ocr-onnx using PaddleOCR v5 ONNX models.")
    }
}
