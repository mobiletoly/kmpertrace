package dev.goquick.kmpertrace.trace

import java.util.concurrent.Executor

/**
 * Wrap this [Runnable] so that logs emitted while it runs remain attached to [snapshot]'s trace/span (if any).
 */
fun Runnable.withTrace(snapshot: TraceSnapshot = captureTraceSnapshot()): Runnable {
    val original = this
    return Runnable { snapshot.withTraceSnapshot { original.run() } }
}

/**
 * Execute [block] with [snapshot] installed on the executor thread for the duration of the callback.
 */
fun Executor.executeWithTrace(snapshot: TraceSnapshot = captureTraceSnapshot(), block: () -> Unit) {
    execute { snapshot.withTraceSnapshot(block) }
}
