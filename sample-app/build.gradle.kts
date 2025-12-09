import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("dev.goquick.kmpertrace.gradle")
}

kmperTrace {
    packageName.set("dev.goquick.kmpertrace.sampleapp.generated")
    className.set("HelloFromPlugin")
    message.set("Hello from the custom KMP plugin!")
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            // Export the runtime so Swift sees its symbols and bundled dependencies.
            export(project(":kmpertrace-runtime"))
            export(libs.kotlinxDatetime)
            export(libs.kotlinxCoroutinesCore)
        }
    }

    wasmJs {
        browser()
        binaries.executable()
    }

    jvm()

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidxActivityCompose)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            api(project(":kmpertrace-runtime"))
            api(libs.kotlinxCoroutinesCore)
            api(libs.kotlinxDatetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinxCoroutinesSwing)
        }
        wasmJsMain.dependencies {
            implementation(npm("@js-joda/core", "5.5.2"))
        }
    }
}

android {
    namespace = "dev.goquick.kmpertrace.sampleapp"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.goquick.kmpertrace.sampleapp"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "dev.goquick.kmpertrace.sampleapp.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "dev.goquick.kmpertrace.sampleapp"
            packageVersion = "1.0.0"
        }
    }
}
