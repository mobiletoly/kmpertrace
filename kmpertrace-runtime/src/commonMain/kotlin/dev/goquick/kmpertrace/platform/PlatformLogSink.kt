package dev.goquick.kmpertrace.platform

import dev.goquick.kmpertrace.log.LogRecord
import dev.goquick.kmpertrace.log.LogSink

/**
 * Default platform sink that writes rendered KmperTrace lines to the platform console/log system.
 */
expect object PlatformLogSink : LogSink {
    override fun emit(record: LogRecord)
}
