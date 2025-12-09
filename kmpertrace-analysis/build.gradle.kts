plugins {
    kotlin("jvm")
    id("com.vanniktech.maven.publish")
}

val kmpertraceGroup: String by project
val kmpertraceVersion: String by project

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

mavenPublishing {
    coordinates(kmpertraceGroup, "kmpertrace-analysis", kmpertraceVersion)
    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set("KmperTrace Analysis")
        description.set("KmperTrace log analysis engine and trace builder")
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
