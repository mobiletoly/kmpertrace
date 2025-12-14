package dev.goquick.kmpertrace.cli.source

import java.io.BufferedReader

object Sources {
    fun resolve(
        sourceOpt: String?,
        filePresent: Boolean,
        adbCmd: String?,
        adbPkg: String?,
        iosCmd: String?,
        iosProc: String?
    ): String {
        val explicit = sourceOpt?.lowercase()
        val inferred = when {
            explicit != null -> explicit
            filePresent -> "file"
            adbCmd != null || adbPkg != null -> "adb"
            iosCmd != null || iosProc != null -> "ios"
            else -> "stdin"
        }
        require(inferred in setOf("file", "adb", "ios", "stdin")) {
            "Invalid --source value: $explicit (use file|adb|ios|stdin)"
        }
        if (inferred == "file" && !filePresent) {
            error("--source=file requires --file")
        }
        return inferred
    }

    fun readerFor(
        resolvedSource: String,
        filePath: java.nio.file.Path?,
        adbCmd: String?,
        adbPkg: String?,
        iosCmd: String?,
        iosProc: String?
    ): BufferedReader = when (resolvedSource) {
        "stdin" -> System.`in`.bufferedReader()
        "file" -> filePath!!.toFile().bufferedReader()
        "adb" -> startProcessReader(SourceCommands.buildAdbCommand(adbCmd, adbPkg))
        "ios" -> startProcessReader(SourceCommands.buildIosCommand(iosCmd, iosProc))
        else -> throw IllegalStateException("Unknown source: $resolvedSource")
    }

    private fun startProcessReader(command: String): BufferedReader {
        val process = ProcessBuilder(listOf("sh", "-c", command))
            .redirectErrorStream(true)
            .start()
        return process.inputStream.bufferedReader()
    }
}
