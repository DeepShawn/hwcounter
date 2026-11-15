plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.deepshawn.hwcounter"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.deepshawn.hwcounter"
        minSdk = 26
        targetSdk = 35
        versionCode = 23
        versionName = "2.2.0"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            storeFile = file("hwcounter.jks")
            storePassword = "hwcounter"
            keyAlias = "hwcounter"
            keyPassword = "hwcounter"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("release")
        }
    }

    splits {
        abi {
            isEnable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

// 界面截图（仅 CI 的 screenshots 工作流传 -Pshots 时启用，平时构建不需要 Robolectric）
val shots = project.hasProperty("shots")
if (shots) {
    android.sourceSets.getByName("test").java.srcDir("src/shots/java")
    android.testOptions.unitTests.isIncludeAndroidResources = true
    android.testOptions.unitTests.all {
        it.systemProperty("robolectric.graphicsMode", "NATIVE")
        it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
        it.systemProperty("roborazzi.test.record", "true")
        it.systemProperty("shots.dir", rootProject.file("shots").absolutePath)
        it.maxHeapSize = "3g"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    if (shots) {
        testImplementation("org.robolectric:robolectric:4.16")
        testImplementation("androidx.compose.ui:ui-test-junit4")
        testImplementation("androidx.test:core:1.6.1")
        testImplementation("io.github.takahirom.roborazzi:roborazzi:1.78.0")
        debugImplementation("androidx.compose.ui:ui-test-manifest")
    }
    val composeBom = platform("androidx.compose:compose-bom:2026.03.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    // HyperOS 风格组件库 + Liquid Glass 液态玻璃（背景模糊/折射）
    implementation("top.yukonga.miuix.kmp:miuix-android:0.8.8")
    implementation("io.github.kyant0:backdrop:1.0.6")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.12.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    val cameraX = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    // 本地离线识别（模型随包，无需联网）
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.google.zxing:core:3.5.3")
}
