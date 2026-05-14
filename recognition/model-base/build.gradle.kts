plugins {
    id("koo.library.kmp")
    id("koo.compose")
}

kotlin {
    explicitApi()

    android {
        namespace = "com.github.arthurkun.koo.recognition.base"
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
    packageOfResClass = "com.github.arthurkun.koo.recognition.base.resources"
}
