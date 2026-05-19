import koo.buildlogic.MAVEN_PUBLISH_GROUP_ID

plugins {
    id("koo.library.kmp")
    id("koo.library.kmp.tests")
    id("koo.compose")
    id("koo.maven.publish")
}

kotlin {
    explicitApi()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }

    android {
        namespace = "com.github.arthurkun.koo"

        optimization {
            consumerKeepRules.file("consumer-rules.pro")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.resources)
            api(project(":kt-ocr-onnx-core"))
            api(project(":detection:detection-core"))
            api(project(":recognition:recognition-core"))
            api(libs.kotlinx.io.core)
            api(project(":recognition:model-core"))
            api(project(":recognition:model-base"))
        }

        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                // Shared sources require ORT symbols, but the concrete runtime must be
                // target-specific to avoid packaging both JVM and Android artifacts.
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
                implementation(libs.opencv.jvm)
            }
        }

        // Share test assets between jvmTest and androidDeviceTest.
        // These are NOT compose component resources (those live in commonMain/composeResources).
        val sharedTestAssetsDir = "src/sharedTestAssets"

        jvmTest {
            resources.srcDir(sharedTestAssetsDir)
        }

        getByName("androidDeviceTest") {
            resources.srcDir(sharedTestAssetsDir)
        }
    }
}

tasks {

    // when running with jvm test with jetbrains runtime jdk 25
    withType<Test> {
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}

compose.resources {
    packageOfResClass = "com.github.arthurkun.koo.resources"
}

mavenPublishing {
    coordinates(
        groupId = MAVEN_PUBLISH_GROUP_ID,
        artifactId = "kt-ocr-onnx",
        version = version.toString(),
    )

    pom {
        name.set("Kt OCR ONNX")
        description.set("Kotlin Multiplatform OCR library using PaddleOCR v5 ONNX models.")
    }
}
