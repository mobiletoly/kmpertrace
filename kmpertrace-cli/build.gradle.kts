plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.clikt)
    implementation(project(":kmpertrace-parse"))
    implementation(project(":kmpertrace-analysis"))
    implementation(libs.mordantJvm)
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("dev.goquick.kmpertrace.cli.MainKt")
}

kotlin {
    jvmToolchain(17)
}

val generateCliBuildInfo by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/buildInfo").get().asFile
    outputs.dir(outputDir)
    doLast {
        val version = project.version.toString()
        val pkgDir = outputDir.resolve("dev/goquick/kmpertrace/cli")
        pkgDir.mkdirs()
        val file = pkgDir.resolve("BuildInfo.kt")
        file.writeText(
            """
            package dev.goquick.kmpertrace.cli

            internal object BuildInfo {
                const val VERSION: String = "$version"
            }
            """.trimIndent()
        )
    }
}

sourceSets {
    named("main") {
        kotlin.srcDir(layout.buildDirectory.dir("generated/buildInfo"))
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateCliBuildInfo)
}
