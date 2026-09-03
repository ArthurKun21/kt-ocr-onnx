import koo.buildlogic.MAVEN_PUBLISH_GROUP_ID

plugins {
    id("koo.library.kmp")
    id("koo.compose")
    id("koo.maven.publish")
}

kotlin {
    explicitApi()

    android {
        namespace = "com.github.arthurkun.koo.detection.v5.base"
        androidResources.enable = true
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":detection:model-core"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.resources)
        }
    }
}

compose.resources {
    packageOfResClass = "com.github.arthurkun.koo.detection.v5.base.resources"
}

mavenPublishing {
    coordinates(
        groupId = MAVEN_PUBLISH_GROUP_ID,
        artifactId = "kt-ocr-onnx-detection-model-v5-base",
        version = version.toString(),
    )

    pom {
        name.set("Kt OCR ONNX Detection Model V5 Base")
        description.set("Bundled PP-OCRv5 detection model resources used by kt-ocr-onnx artifacts.")
    }
}
