package dev.goquick.kmpertrace.trace

import dev.goquick.kmpertrace.log.LogRecord
import dev.goquick.kmpertrace.log.LogSink
import platform.Foundation.NSLock

internal class IosCollectingSink : LogSink {
    private val lock = NSLock()
    private val records = mutableListOf<LogRecord>()

    override fun emit(record: LogRecord) {
        lock.lock()
        try {
            records += record
        } finally {
            lock.unlock()
        }
    }

    fun snapshot(): List<LogRecord> {
        lock.lock()
        return try {
            records.toList()
        } finally {
            lock.unlock()
        }
    }

    fun clear() {
        lock.lock()
        try {
            records.clear()
        } finally {
            lock.unlock()
        }
    }
}
