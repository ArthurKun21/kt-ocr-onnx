import koo.buildlogic.MAVEN_PUBLISH_GROUP_ID

plugins {
    id("koo.library.kmp")
    id("koo.maven.publish")
}

kotlin {
    explicitApi()

    android {
        namespace = "com.github.arthurkun.koo.detection.core"
    }
}

mavenPublishing {
    coordinates(
        groupId = MAVEN_PUBLISH_GROUP_ID,
        artifactId = "kt-ocr-onnx-detection-model-core",
        version = version.toString(),
    )

    pom {
        name.set("Kt OCR ONNX Model Core")
        description.set("Shared detection model abstractions used by kt-ocr-onnx artifacts.")
    }
}
