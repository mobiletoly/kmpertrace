package dev.goquick.kmpertrace.trace

import dev.goquick.kmpertrace.log.LogRecord
import dev.goquick.kmpertrace.log.LogSink

internal class AndroidCollectingSink : LogSink {
    val records = mutableListOf<LogRecord>()
    override fun emit(record: LogRecord) {
        records += record
    }
}
