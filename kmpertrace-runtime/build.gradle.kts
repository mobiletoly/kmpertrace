import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("com.vanniktech.maven.publish")
}

val kmpertraceGroup: String by project
val kmpertraceVersion: String by project

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    val xcf = XCFramework("KmperTraceRuntime")

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-opt-in=kotlin.time.ExperimentalTime"
        )
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    val iosTargets = listOf(
        iosArm64(),
        iosSimulatorArm64()
    )

    wasmJs {
        browser()
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinxCoroutinesCore)
            api(libs.kotlinxDatetime)
        }
        iosMain.dependencies {
            implementation(libs.kotlinxCoroutinesCore)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinxCoroutinesTest)
        }
        wasmJsMain.dependencies {
            implementation(npm("@js-joda/core", "5.5.2"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
        }
        iosTest.dependencies {
            implementation(kotlin("test"))
        }
    }

    iosTargets.forEach { target ->
        target.binaries.framework {
            baseName = "KmperTraceRuntime"
            isStatic = true
            xcf.add(this)
        }
    }
}

// Ensure native frameworks export our dependencies when consumed from other KMP/iOS modules.
kotlin.targets.withType(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget::class.java).all {
    binaries.withType(org.jetbrains.kotlin.gradle.plugin.mpp.Framework::class.java).all {
        export(libs.kotlinxDatetime)
        export(libs.kotlinxCoroutinesCore)
    }
}

tasks.register("prepareSpmRelease") {
    dependsOn("assembleKmperTraceRuntimeReleaseXCFramework")
    group = "distribution"
    description = "Build release XCFramework, zip it, compute checksum, and render Package.swift for SwiftPM binary distribution."

    doLast {
        val version: String by project
        val releaseDir = layout.buildDirectory.dir("XCFrameworks/release").get()
        val xcframeworkDir = releaseDir.dir("KmperTraceRuntime.xcframework")
        check(xcframeworkDir.asFile.exists()) {
            "XCFramework not found at ${xcframeworkDir.asFile}. Run assembleKmperTraceRuntimeReleaseXCFramework first."
        }

        val zipFile = releaseDir.file("KmperTraceRuntime.xcframework.zip")
        zipFile.asFile.delete()
        project.exec {
            workingDir = releaseDir.asFile
            commandLine("zip", "-r", zipFile.asFile.name, xcframeworkDir.asFile.name)
        }

        val checksumOutput = ByteArrayOutputStream()
        project.exec {
            workingDir = releaseDir.asFile
            commandLine("shasum", "-a", "256", zipFile.asFile.name)
            standardOutput = checksumOutput
        }
        val checksum = checksumOutput.toString().trim().split(" ").firstOrNull()
            ?: error("Failed to parse checksum")

        val template = rootProject.file("Package.swift.template").readText()
        val url = "https://github.com/mobiletoly/kmpertrace/releases/download/v$version/KmperTraceRuntime.xcframework.zip"
        val rendered = template
            .replace("__URL__", url)
            .replace("__CHECKSUM__", checksum)

        rootProject.file("Package.swift").writeText(rendered)

        println("[KmperTrace] Prepared Package.swift for v$version")
        println("[KmperTrace] URL: $url")
        println("[KmperTrace] SHA-256: $checksum")
    }
}

android {
    namespace = "dev.goquick.kmpertrace.runtime"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Work around Kotlin/Native test runner instability with TeamCity service messages on iOS simulators.
// Without this, occasional segfaults/“Received output for test that is not running” can surface on fresh daemons/CI.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>().configureEach {
    environment("KOTLIN_NATIVE_IGNORE_SERVICE_MESSAGES", "true")
}

mavenPublishing {
    coordinates(kmpertraceGroup, "kmpertrace-runtime", kmpertraceVersion)
    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set("KmperTrace Runtime")
        description.set("Multiplatform runtime for KmperTrace logging and tracing")
        url.set("https://github.com/mobiletoly/kmpertrace")
        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id = "mobiletoly"
                name = "Toly Pochkin"
                url = "https://github.com/mobiletoly"
            }
        }
        scm {
            connection = "scm:git:https://github.com/mobiletoly/kmpertrace.git"
            developerConnection = "scm:git:ssh://git@github.com/mobiletoly/kmpertrace.git"
            url = "https://github.com/mobiletoly/kmpertrace"
        }
    }
}
