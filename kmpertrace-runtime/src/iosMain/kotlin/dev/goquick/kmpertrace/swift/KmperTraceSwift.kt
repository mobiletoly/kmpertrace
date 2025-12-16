package dev.goquick.kmpertrace.swift

import dev.goquick.kmpertrace.trace.captureTraceSnapshot

/**
 * Swift-facing facade for emitting KmperTrace logs and bridging trace/span binding across
 * non-coroutine async boundaries.
 *
 * Notes:
 * - Swift cannot ergonomically call Kotlin `() -> String` message lambdas, so these APIs accept plain Strings.
 * - Use [captureSnapshot] while a span is active, then call [withSnapshot] in Swift callbacks to re-install
 *   the trace/span binding for the duration of the block.
 */
object KmperTraceSwift {
    /**
     * Capture the current trace/span + log binding state into an immutable [KmperTraceSnapshot].
     */
    fun captureSnapshot(): KmperTraceSnapshot = KmperTraceSnapshot(captureTraceSnapshot())

    /**
     * Run [block] with [snapshot] installed for the duration of the block, then restore previous state.
     *
     * This does not create a span; it only makes KmperTrace logging bind to the captured span context.
     */
    fun withSnapshot(snapshot: KmperTraceSnapshot?, block: () -> Unit) {
        if (snapshot == null) {
            block()
        } else {
            snapshot.raw.withTraceSnapshot(block)
        }
    }

    /**
     * Build a component-bound logger to avoid repeating `component` at each callsite.
     */
    fun logger(component: String): KmperLogger = KmperLogger(component = component)

    /**
     * Build a component-bound logger that automatically installs [snapshot] (when present) for each log call.
     */
    fun snapshotLogger(component: String, snapshot: KmperTraceSnapshot? = null): KmperSnapshotLogger =
        KmperSnapshotLogger(logger = KmperLogger(component = component), snapshot = snapshot)
}
