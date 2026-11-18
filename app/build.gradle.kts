import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val appId = providers.gradleProperty("diviora.applicationId").get()

android {
    namespace = "com.diviora.music"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig {
        applicationId = appId
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("diviora.versionCode").get().toInt()
        versionName = providers.gradleProperty("diviora.versionName").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Developer content server: the catalog every install loads (empty = bundled demo catalog). See docs/catalog.md.
        buildConfigField("String", "CATALOG_URL", "\"${providers.gradleProperty("diviora.catalogUrl").getOrElse("")}\"")
    }
    signingConfigs {
        // Stable, intentionally public key for APKs downloaded from GitHub Releases, so updates install over
        // older versions. Google Play builds use the private release.jks (CI secret) instead.
        create("sideload") {
            storeFile = file("sideload.keystore")
            storePassword = "diviora-sideload"
            keyAlias = "diviora"
            keyPassword = "diviora-sideload"
        }
        create("release") {
            val ks = rootProject.file("release.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = System.getenv("DIVIORA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DIVIORA_KEY_ALIAS")
                keyPassword = System.getenv("DIVIORA_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (rootProject.file("release.jks").exists()) "release" else "sideload")
        }
        debug { applicationIdSuffix = ".debug"; signingConfig = signingConfigs.getByName("sideload") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "ObsoleteSdkInt")
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:catalog"))
    implementation(project(":scheduler:recurrence"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.session)
    implementation(libs.media3.ui.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
