import koo.buildlogic.MAVEN_PUBLISH_GROUP_ID

plugins {
    id("koo.library.kmp")
    id("koo.compose")
    id("koo.maven.publish")
}

kotlin {
    explicitApi()

    android {
        namespace = "com.github.arthurkun.koo.recognition.v5.kr"
        androidResources.enable = true
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":recognition:model-core"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.resources)
        }
    }
}

compose.resources {
    packageOfResClass = "com.github.arthurkun.koo.recognition.v5.kr.resources"
}

mavenPublishing {
    coordinates(
        groupId = MAVEN_PUBLISH_GROUP_ID,
        artifactId = "kt-ocr-onnx-recognition-model-v5-kr",
        version = version.toString(),
    )

    pom {
        name.set("Kt OCR ONNX Model V5 KR")
        description.set("Korean PP-OCRv5 recognition model resources used by kt-ocr-onnx artifacts.")
    }
}
