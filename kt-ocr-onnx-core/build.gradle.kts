import koo.buildlogic.MAVEN_PUBLISH_GROUP_ID

plugins {
    id("koo.library.kmp")
    id("koo.maven.publish")
}

kotlin {
    explicitApi()

    android {
        namespace = "com.github.arthurkun.koo.core"
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.opencv.android)
        }

        jvmMain.dependencies {
            implementation(libs.opencv.jvm)
        }
    }
}

mavenPublishing {
    coordinates(
        groupId = MAVEN_PUBLISH_GROUP_ID,
        artifactId = "kt-ocr-onnx-core",
        version = version.toString(),
    )

    pom {
        name.set("Kt OCR ONNX Core")
        description.set("Core APIs and shared imaging primitives for kt-ocr-onnx.")
    }
}
