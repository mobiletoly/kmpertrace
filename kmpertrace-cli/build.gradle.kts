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
