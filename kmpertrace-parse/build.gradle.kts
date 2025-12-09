plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("com.vanniktech.maven.publish")
}

kotlin {
    jvm()
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

val kmpertraceGroup: String by project
val kmpertraceVersion: String by project

repositories {
    mavenCentral()
}

mavenPublishing {
    coordinates(kmpertraceGroup, "kmpertrace-parse", kmpertraceVersion)
    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set("KmperTrace Parse")
        description.set("Structured KmperTrace log parser")
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
