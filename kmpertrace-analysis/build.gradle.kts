plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":kmpertrace-parse"))
    testImplementation(kotlin("test"))
}

repositories {
    mavenCentral()
}
