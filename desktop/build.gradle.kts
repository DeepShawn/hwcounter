import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// 作业通 Windows 客户端：只读查看老师公开的数据（与网页版功能相同），
// 数据模型和全部计分 / 统计算法直接复用安卓 app 模块的 data 包源码，三端算法零差异。
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val appVersion: String = Regex("versionName = \"([^\"]+)\"").find(rootProject.file("app/build.gradle.kts").readText())!!.groupValues[1]

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

// 与安卓共用的源码：data 包（去掉安卓专用的 AppViewModel）+ 考试导入用到的两个识别工具
val sharedSrc by tasks.registering(Sync::class) {
    from("../app/src/main/java") {
        include("com/deepshawn/hwcounter/data/**")
        exclude("**/AppViewModel.kt")
        include("com/deepshawn/hwcounter/recog/SeatingLogic.kt", "com/deepshawn/hwcounter/recog/StatTable.kt")
    }
    into(layout.buildDirectory.dir("generated/shared"))
}
kotlin.sourceSets.main { kotlin.srcDir(sharedSrc) }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("top.yukonga.miuix.kmp:miuix-desktop:0.8.8")
    // 纯线条图标（Lucide）：界面不用 emoji
    implementation("com.composables:icons-lucide-cmp:2.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    testImplementation(kotlin("test"))
}

// 生成版本号常量
val genDir = layout.buildDirectory.dir("generated/version")
val genVersion by tasks.registering {
    val out = genDir
    inputs.property("v", appVersion)
    outputs.dir(out)
    doLast {
        val f = out.get().file("com/deepshawn/hwcounter/desktop/Version.kt").asFile
        f.parentFile.mkdirs()
        f.writeText("package com.deepshawn.hwcounter.desktop\n\nconst val APP_VERSION = \"$appVersion\"\n")
    }
}
kotlin.sourceSets.main { kotlin.srcDir(genVersion) }

compose.desktop {
    application {
        mainClass = "com.deepshawn.hwcounter.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "HwCounter"
            packageVersion = appVersion.split(".").let { p -> (p + listOf("0", "0", "0")).take(3).joinToString(".") }
            // 安装包元数据必须是 ASCII（WiX 默认 1252 代码页，中文会导致打包失败）
            description = "HwCounter homework viewer"
            vendor = "DeepShawn"
            copyright = "DeepShawn"
            modules("java.net.http", "java.prefs", "jdk.crypto.ec", "jdk.localedata", "jdk.charsets")
            windows {
                menuGroup = "HwCounter"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "5f7c2a1e-3b8d-4c4e-9a61-2d0b6f9e8c11"
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
        }
    }
}

// 截图：在没有显示器的环境里离屏渲染各个页面（./gradlew :desktop:shots，输出到 build/shots）
tasks.register<JavaExec>("shots") {
    group = "verification"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.deepshawn.hwcounter.desktop.ShotsKt")
    args(layout.buildDirectory.dir("shots").get().asFile.absolutePath)
}
