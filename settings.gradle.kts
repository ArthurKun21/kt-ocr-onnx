pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // fallback for the rest of the dependencies
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "kt-ocr-onnx"
include(":kt-ocr-onnx-core")
include(":kt-ocr-onnx")
include(":kt-ocr-onnx-detection")
include(":kt-ocr-onnx-recognition")
include(":detection:detection-core")
include(":detection:model-core")
include(":detection:model-base")
include(":detection:model-v5-base")
include(":recognition:recognition-core")
include(":recognition:model-core")
include(":recognition:model-base")
include(":recognition:model-v5-base")
include(":recognition:model-v5-kr")
