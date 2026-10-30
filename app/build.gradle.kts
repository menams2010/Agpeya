import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val releaseSigningPropertiesFile = rootProject.file("keystore.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.isFile) {
        releaseSigningPropertiesFile.inputStream().use { load(it) }
    }
}
val releaseSigningValues = listOf(
    "storeFile",
    "storePassword",
    "keyAlias",
    "keyPassword",
).map { releaseSigningProperties.getProperty(it)?.takeIf(String::isNotBlank) }
val hasReleaseSigning = releaseSigningValues.all { it != null }

android {
    namespace = "com.mina.agpeya"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mina.agpeya"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.create("localRelease").apply {
                    storeFile = file(checkNotNull(releaseSigningValues[0]))
                    storePassword = checkNotNull(releaseSigningValues[1])
                    keyAlias = checkNotNull(releaseSigningValues[2])
                    keyPassword = checkNotNull(releaseSigningValues[3])
                }
            }
            optimization {
                enable = true
                packageScope = setOf("com.mina.agpeya.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

val signedReleaseApk = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
val adbExecutable = androidComponents.sdkComponents.adb

tasks.register("installSignedRelease") {
    group = "installation"
    description = "Build, install, and launch the signed release APK."

    dependsOn("assembleRelease")
    val apk = signedReleaseApk
    val adb = adbExecutable
    doLast {
        val apkFile = apk.get().asFile
        check(apkFile.isFile) {
            "Signed release APK not found at ${apkFile.absolutePath}"
        }

        val adbFile = adb.get().asFile

        listOf(
            listOf("install", "-r", apkFile.absolutePath),
            listOf("shell", "monkey", "-p", "com.mina.agpeya", "1"),
        ).forEach { arguments ->
            val process = ProcessBuilder(listOf(adbFile.absolutePath) + arguments)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            check(process.waitFor() == 0) {
                "ADB ${arguments.first()} failed: $output"
            }
            println(output.trim())
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.material)
    implementation(libs.google.play.services.auth)
    implementation(libs.androidx.work)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
