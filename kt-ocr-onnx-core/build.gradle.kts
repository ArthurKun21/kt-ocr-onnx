plugins {
    id("koo.library.kmp")
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
