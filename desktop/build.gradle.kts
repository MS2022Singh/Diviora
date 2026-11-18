import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

kotlin { jvmToolchain(21) }

// JavaFX media (GPLv2 + Classpath Exception, so linking from proprietary code is allowed) needs per-OS native jars.
val osName = System.getProperty("os.name").lowercase()
val osArch = System.getProperty("os.arch").lowercase()
val fxClassifier = when {
    "win" in osName -> "win"
    "mac" in osName -> if (osArch == "aarch64") "mac-aarch64" else "mac"
    else -> if (osArch == "aarch64") "linux-aarch64" else "linux"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:catalog"))
    implementation(project(":scheduler:recurrence"))
    implementation(compose.desktop.currentOs)
    implementation(libs.jb.material3)
    implementation(libs.jb.material.icons)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    for (m in listOf("base", "graphics", "media", "swing")) {
        implementation("org.openjfx:javafx-$m:${libs.versions.javafx.get()}:$fxClassifier")
    }
    testImplementation(libs.junit)
}

val appVersion = providers.gradleProperty("diviora.versionName").get()
val versionCode = providers.gradleProperty("diviora.versionCode").get()

compose.desktop {
    application {
        mainClass = "com.diviora.music.desktop.MainKt"
        jvmArgs += listOf("-Ddiviora.version=$appVersion", "-Ddiviora.catalogUrl=${providers.gradleProperty("diviora.catalogUrl").getOrElse("")}")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "DIVIORA Music"
            // Installers require MAJOR > 0; this maps app versionCode N to 1.0.N.
            packageVersion = "1.0.$versionCode"
            description = "DIVIORA Music: audio/video player with AutoPlay Routines"
            vendor = "DIVIORA Music"
            copyright = "© 2026 DIVIORA Music. All rights reserved."
            modules("java.desktop", "java.net.http", "java.logging", "java.naming", "java.scripting", "jdk.unsupported", "jdk.jsobject", "jdk.xml.dom")
            windows { menu = true; shortcut = true; perUserInstall = true; dirChooser = true; upgradeUuid = "6f1c7a0e-3c55-4c0e-9a7e-5d1d2b0c4e21" }
            macOS { bundleID = "com.diviora.music.desktop" }
            linux { shortcut = true; menuGroup = "AudioVideo" }
        }
    }
}
