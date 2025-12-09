package dev.goquick.kmpertrace

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import javax.inject.Inject

/**
 * Extension for configuring the KmperTrace source generator.
 */
abstract class KmperTraceExtension @Inject constructor(objects: ObjectFactory) {
    /**
     * Package name for the generated Kotlin source.
     */
    val packageName: Property<String> = objects.property(String::class.java)

    /**
     * Class name for the generated Kotlin source.
     */
    val className: Property<String> = objects.property(String::class.java)

    /**
     * Message content stored in the generated `MESSAGE` constant.
     */
    val message: Property<String> = objects.property(String::class.java)
}

/**
 * Gradle plugin that generates a simple shared Kotlin source and wires it into KMP builds.
 */
class KmperTracePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("kmperTrace", KmperTraceExtension::class.java).apply {
            packageName.convention("dev.goquick.kmpertrace.generated")
            className.convention("GeneratedGreeting")
            message.convention("Hello from KmperTrace plugin!")
        }

        val outputDir = project.layout.buildDirectory.dir("generated/src/commonMain/kotlin")
        val generateTask = project.tasks.register(
            "generateKmperTraceSources",
            GenerateKmperTraceTask::class.java
        ) { task ->
            task.group = "code generation"
            task.description = "Generates shared Kotlin sources configured via the kmperTrace extension."
            task.packageName.set(extension.packageName)
            task.className.set(extension.className)
            task.message.set(extension.message)
            task.outputDirectory.set(outputDir)
        }

        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kotlinExt = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            kotlinExt.sourceSets.named("commonMain").configure { sourceSet ->
                sourceSet.kotlin.srcDir(generateTask.flatMap { it.outputDirectory })
            }

            project.tasks.withType(KotlinCompilationTask::class.java).configureEach { compileTask ->
                compileTask.dependsOn(generateTask)
            }
        }

        project.tasks.register("kmperTraceDoctor") { task ->
            task.group = "verification"
            task.description = "Prints a short diagnostic about the current Gradle project."
            task.doLast {
                project.logger.lifecycle(
                    "[kmpertrace] package=${extension.packageName.get()}, class=${extension.className.get()}, message=${extension.message.get()}"
                )
            }
        }
    }
}
