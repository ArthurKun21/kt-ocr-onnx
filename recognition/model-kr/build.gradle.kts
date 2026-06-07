import koo.buildlogic.MAVEN_PUBLISH_GROUP_ID

plugins {
    id("koo.library.kmp")
    id("koo.compose")
    id("koo.maven.publish")
}

kotlin {
    explicitApi()

    android {
        namespace = "com.github.arthurkun.koo.recognition.kr"
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
    packageOfResClass = "com.github.arthurkun.koo.recognition.kr.resources"
}

mavenPublishing {
    coordinates(
        groupId = MAVEN_PUBLISH_GROUP_ID,
        artifactId = "kt-ocr-onnx-recognition-model-kr",
        version = version.toString(),
    )

    pom {
        name.set("Kt OCR ONNX Model KR")
        description.set("Korean recognition model resources used by kt-ocr-onnx artifacts.")
    }
}
