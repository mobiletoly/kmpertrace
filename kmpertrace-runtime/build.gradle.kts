import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("com.vanniktech.maven.publish")
}

val kmpertraceGroup: String by project
val kmpertraceVersion: String by project

@OptIn(ExperimentalWasmDsl::class)
kotlin {
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

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    )

    wasmJs {
        browser()
    }

    jvm()

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
}

// Ensure native frameworks export our dependencies when consumed from other KMP/iOS modules.
kotlin.targets.withType(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget::class.java).all {
    binaries.withType(org.jetbrains.kotlin.gradle.plugin.mpp.Framework::class.java).all {
        export(libs.kotlinxDatetime)
        export(libs.kotlinxCoroutinesCore)
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
