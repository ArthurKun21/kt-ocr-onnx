import com.android.build.gradle.tasks.MergeSourceSetFolders
import koo.buildlogic.MAVEN_PUBLISH_GROUP_ID

val sharedTestAssetsDir = "src/sharedTestAssets"
val sharedTestAssets = layout.projectDirectory.dir(sharedTestAssetsDir)

plugins {
    id("koo.library.kmp")
    id("koo.library.kmp.tests")
    id("koo.compose")
    id("koo.maven.publish")
    id("koo.opencv.jvm.platform")
}

kotlin {
    explicitApi()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()

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
            api(libs.kotlinx.io.core)
            api(project(":recognition:model-core"))
            api(project(":recognition:model-base"))
            api(project(":detection:model-core"))
            api(project(":detection:model-base"))
            implementation(project(":detection:detection-core"))
            implementation(project(":recognition:recognition-core"))
        }

        val jvmCommonMain = create("jvmCommonMain") {
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
            }
        }

        // Share test assets with jvmTest via classpath resources and Android device tests via assets.
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

tasks {
    withType<MergeSourceSetFolders>().matching { it.name == "mergeAndroidDeviceTestAssets" }.configureEach {
        sourceFolderInputs.from(sharedTestAssets)
    }

    matching { it.name == "compressAndroidDeviceTestAssets" }.configureEach {
        dependsOn(copySharedAndroidDeviceTestAssets)
    }

    matching { it.name == "copyAndroidDeviceTestComposeResourcesToAndroidAssets" }.configureEach {
        enabled = false
    }

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
