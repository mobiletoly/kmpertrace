package dev.goquick.kmpertrace.trace.android

import android.os.Handler
import dev.goquick.kmpertrace.trace.TraceSnapshot
import dev.goquick.kmpertrace.trace.captureTraceSnapshot

/**
 * Post a callback that runs with [snapshot] installed for the duration of the block, so logs bind to the
 * originating trace/span when crossing non-coroutine async boundaries (Handler/Looper).
 */
fun Handler.postWithTrace(snapshot: TraceSnapshot = captureTraceSnapshot(), block: () -> Unit): Boolean =
    post { snapshot.withTraceSnapshot(block) }

/**
 * Post a delayed callback that runs with [snapshot] installed for the duration of the block, so logs bind to the
 * originating trace/span when crossing non-coroutine async boundaries (Handler/Looper).
 */
fun Handler.postDelayedWithTrace(
    delayMillis: Long,
    snapshot: TraceSnapshot = captureTraceSnapshot(),
    block: () -> Unit
): Boolean = postDelayed({ snapshot.withTraceSnapshot(block) }, delayMillis)
