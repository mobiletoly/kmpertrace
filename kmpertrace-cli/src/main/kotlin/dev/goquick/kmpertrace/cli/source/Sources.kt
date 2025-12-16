package dev.goquick.kmpertrace.cli.source

import dev.goquick.kmpertrace.cli.usageError
import java.io.BufferedReader
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

object Sources {
    fun resolve(
        sourceOpt: String?,
        filePresent: Boolean,
        adbCmd: String?,
        adbPkg: String?,
        iosCmd: String?,
        iosProc: String?,
        iosTarget: String?,
        iosUdid: String?
    ): String {
        val explicit = sourceOpt?.lowercase()
        val inferred = when {
            explicit != null -> explicit
            filePresent -> "file"
            adbCmd != null || adbPkg != null -> "adb"
            iosCmd != null || iosProc != null || iosTarget != null || iosUdid != null -> "ios"
            else -> "stdin"
        }
        if (inferred !in setOf("file", "adb", "ios", "stdin")) {
            usageError("Invalid --source value: $explicit (use file|adb|ios|stdin)")
        }
        if (inferred == "file" && !filePresent) {
            usageError("--source=file requires --file")
        }
        return inferred
    }

    fun readerFor(
        resolvedSource: String,
        filePath: java.nio.file.Path?,
        adbCmd: String?,
        adbPkg: String?,
        iosCmd: String?,
        iosProc: String?,
        iosTarget: String?,
        iosUdid: String?,
        rawLogsEnabled: Boolean
    ): BufferedReader = when (resolvedSource) {
        "stdin" -> System.`in`.bufferedReader()
        "file" -> filePath!!.toFile().bufferedReader()
        "adb" -> startProcessReader(SourceCommands.buildAdbCommand(adbCmd, adbPkg))
        "ios" -> {
            val resolvedIos = IosDiscovery.resolveIosSource(
                iosCmd = iosCmd,
                iosProc = iosProc,
                iosTarget = iosTarget,
                iosUdid = iosUdid
            )
            if (resolvedIos.kind == IosDiscovery.IosSourceKind.DEVICE) {
                val processor = IdeviceSyslogLineProcessor(iosProc = iosProc)
                startProcessReader(resolvedIos.command, lineProcessor = processor::process)
            } else {
                startProcessReader(resolvedIos.command)
            }
        }
        else -> throw IllegalStateException("Unknown source: $resolvedSource")
    }

    private fun startProcessReader(command: String, lineProcessor: ((String) -> String?)? = null): BufferedReader {
        val process = ProcessBuilder(listOf("sh", "-c", command))
            .redirectErrorStream(true)
            .start()
        val baseReader = process.inputStream.bufferedReader()
        val closed = AtomicBoolean(false)

        fun killProcess() {
            if (!closed.compareAndSet(false, true)) return
            try {
                process.destroy()
                if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                    process.waitFor(500, TimeUnit.MILLISECONDS)
                }
            } catch (_: Exception) {
                // ignore
            }
        }

        Runtime.getRuntime().addShutdownHook(Thread { killProcess() })

        if (lineProcessor == null) {
            return object : BufferedReader(baseReader) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        killProcess()
                    }
                }
            }
        }

        val pipedOut = PipedOutputStream()
        val pipedIn = PipedInputStream(pipedOut)
        val writer = OutputStreamWriter(pipedOut, Charsets.UTF_8)

        thread(isDaemon = true, name = "kmpertrace-cli-line-filter") {
            writer.use { w ->
                while (true) {
                    val line = try {
                        baseReader.readLine()
                    } catch (_: IOException) {
                        null
                    } ?: break
                    val out = lineProcessor(line) ?: continue
                    if (out.isEmpty()) continue
                    try {
                        w.write(out)
                        w.write("\n")
                        w.flush()
                    } catch (_: IOException) {
                        break
                    }
                }
            }
        }

        val filteredReader = pipedIn.bufferedReader()
        return object : BufferedReader(filteredReader) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    try {
                        baseReader.close()
                    } catch (_: Exception) {
                        // ignore
                    }
                    try {
                        pipedOut.close()
                    } catch (_: Exception) {
                        // ignore
                    }
                    killProcess()
                }
            }
        }
    }
}
