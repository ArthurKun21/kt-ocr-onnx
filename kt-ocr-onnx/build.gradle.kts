plugins {
    id("koo.library.kmp")
    id("koo.library.kmp.tests")
    id("koo.compose")
    alias(libs.plugins.vanniktech.maven.publish)
    alias(libs.plugins.binary.compatibility.validator)
}

kotlin {
    explicitApi()

    android {
        namespace = "com.github.arthurkun.koo"
        androidResources.enable = true

        optimization {
            consumerKeepRules.file("consumer-rules.pro")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.resources)
        }

        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.onnxruntime.jvm)
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
    coordinates("com.github.ArthurKun21", "kt-ocr-onnx", version.toString())

    pom {
        name.set("kt-ocr-onnx")
        description.set("Kotlin Multiplatform OCR library using PaddleOCR v5 ONNX models.")
        url.set("https://github.com/ArthurKun21/kt-ocr-onnx")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("ArthurKun21")
                name.set("Arthur")
                email.set("16458204+ArthurKun21@users.noreply.github.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/ArthurKun21/kt-ocr-onnx.git")
            developerConnection.set("scm:git:ssh://github.com/ArthurKun21/kt-ocr-onnx.git")
            url.set("https://github.com/ArthurKun21/kt-ocr-onnx")
        }
    }
}
