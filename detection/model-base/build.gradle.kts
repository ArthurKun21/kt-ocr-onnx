import koo.buildlogic.MAVEN_PUBLISH_GROUP_ID

plugins {
    id("koo.library.kmp")
    id("koo.compose")
    id("koo.maven.publish")
}

kotlin {
    explicitApi()

    android {
        namespace = "com.github.arthurkun.koo.detection.base"
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
    packageOfResClass = "com.github.arthurkun.koo.detection.base.resources"
}

mavenPublishing {
    coordinates(
        groupId = MAVEN_PUBLISH_GROUP_ID,
        artifactId = "kt-ocr-onnx-detection-model-base",
        version = version.toString(),
    )

    pom {
        name.set("Kt OCR ONNX Model Base")
        description.set("Bundled detection model resources used by kt-ocr-onnx artifacts.")
    }
}
