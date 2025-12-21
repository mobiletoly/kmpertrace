pluginManagement {
    includeBuild("plugin")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.3.0"
        id("org.jetbrains.compose") version "1.9.3"
        id("com.android.application") version "8.13.2"
        id("com.android.kotlin.multiplatform.library") version "8.13.2"
        id("com.android.lint") version "8.13.2"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "kmpertrace"
include("sample-app")
include(":sample-app:androidApp")
include("kmpertrace-runtime")
include("kmpertrace-cli")
include("kmpertrace-parse")
include("kmpertrace-analysis")
includeBuild("plugin")
