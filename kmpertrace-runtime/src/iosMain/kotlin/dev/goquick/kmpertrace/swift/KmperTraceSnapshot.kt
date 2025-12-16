package dev.goquick.kmpertrace.swift

import dev.goquick.kmpertrace.trace.TraceSnapshot

/**
 * Swift-facing wrapper around KmperTrace's internal [TraceSnapshot].
 *
 * Why this exists:
 * - Swift projects shouldn't have to reference KMP-internal types like `dev.goquick.kmpertrace.trace.TraceSnapshot`.
 * - Kotlin/Native interop treats Kotlin function-type parameters as escaping closures in Swift; this wrapper makes
 *   it explicit that blocks passed here are allowed to escape.
 *
 * Use [KmperTraceSwift.captureSnapshot] while a span is active, then use [with] in Swift callbacks to re-install
 * the trace/span binding when crossing async boundaries (delegates, GCD, third-party SDK callbacks).
 */
class KmperTraceSnapshot internal constructor(
    internal val raw: TraceSnapshot
) {
    /**
     * Run [block] with this snapshot installed for the duration of the block, then restore previous state.
     *
     * This does not create a span; it only affects which trace/span IDs and binding mode are visible to log
     * emission while [block] executes.
     *
     * Note for Swift: [block] is effectively treated as an escaping closure by Kotlin/Native interop.
     */
    fun with(block: () -> Unit) {
        raw.withTraceSnapshot(block)
    }

    /**
     * Same as [with], but returns the block result.
     *
     * Note for Swift: generics are erased across Kotlin/Native interop, so Swift will typically see `Any?`.
     */
    fun <T> withResult(block: () -> T): T = raw.withTraceSnapshot(block)
}

