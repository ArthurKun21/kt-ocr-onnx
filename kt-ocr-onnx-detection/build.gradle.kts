import com.android.build.gradle.tasks.MergeSourceSetFolders
import koo.buildlogic.MAVEN_PUBLISH_GROUP_ID
import org.gradle.api.tasks.Copy

val sharedTestAssetsDir = "../kt-ocr-onnx/src/sharedTestAssets"
val sharedTestAssets = layout.projectDirectory.dir(sharedTestAssetsDir)

plugins {
    id("koo.library.kmp")
    id("koo.library.kmp.tests")
    id("koo.maven.publish")
}

kotlin {
    explicitApi()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()

    android {
        namespace = "com.github.arthurkun.koo.detection.api"

        optimization {
            consumerKeepRules.file("consumer-rules.pro")
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":kt-ocr-onnx-core"))
            api(libs.kotlinx.io.core)
            implementation(project(":detection:detection-core"))
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

val copySharedAndroidDeviceTestAssets = tasks.register<Copy>("copySharedAndroidDeviceTestAssets") {
    from(sharedTestAssets)
    into(layout.buildDirectory.dir("intermediates/assets/androidDeviceTest/mergeAndroidDeviceTestAssets"))
    dependsOn("mergeAndroidDeviceTestAssets")
}

tasks.withType<MergeSourceSetFolders>().matching { it.name == "mergeAndroidDeviceTestAssets" }.configureEach {
    sourceFolderInputs.from(sharedTestAssets)
}

tasks.matching { it.name == "compressAndroidDeviceTestAssets" }.configureEach {
    dependsOn(copySharedAndroidDeviceTestAssets)
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
