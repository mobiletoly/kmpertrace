package dev.goquick.kmpertrace.trace

import dev.goquick.kmpertrace.core.TraceContext
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Opaque snapshot of the current trace context and log binding.
 *
 * This is intended for bridging non-coroutine async boundaries (e.g. callbacks, handlers, executors)
 * so logs emitted later can still attach to the originating span.
 */
class TraceSnapshot internal constructor(
    private val traceContext: TraceContext?,
    private val loggingBinding: LoggingBinding
) {
    /**
     * Temporarily installs this snapshot into the current execution context, runs [block],
     * and then restores whatever was active before.
     *
     * This does not create a span; it only affects which trace/span IDs and binding mode
     * are visible to log emission while [block] executes.
     */
    fun <T> withTraceSnapshot(block: () -> T): T {
        val previousTrace = TraceContextStorage.get()
        val previousBinding = LoggingBindingStorage.get()

        TraceContextStorage.set(traceContext)
        LoggingBindingStorage.set(loggingBinding)
        return try {
            block()
        } finally {
            TraceContextStorage.set(previousTrace)
            LoggingBindingStorage.set(previousBinding)
        }
    }

    /**
     * Build a [CoroutineContext] that propagates this snapshot across suspending boundaries.
     *
     * This is useful when you *can* stay in coroutines (e.g., `withContext(snapshot.asCoroutineContext()) { ... }`)
     * but want to make the binding explicit when crossing into a scope that otherwise has no trace context.
     *
     * [downstream] should be the existing interceptor/dispatcher chain when you need to preserve it.
     */
    fun asCoroutineContext(downstream: ContinuationInterceptor? = null): CoroutineContext {
        val tracePropagation =
            traceContext?.let { TraceContextStorage.element(it, downstream) } ?: EmptyCoroutineContext

        return (traceContext ?: EmptyCoroutineContext) +
            tracePropagation +
            LoggingBindingStorage.element(loggingBinding)
    }
}

/**
 * Capture the current trace/span + log binding state into an immutable [TraceSnapshot].
 */
fun captureTraceSnapshot(): TraceSnapshot =
    TraceSnapshot(
        traceContext = TraceContextStorage.get(),
        loggingBinding = LoggingBindingStorage.get()
    )

/**
 * Wrap a callback so that when it runs, logs remain attached to [snapshot]'s trace/span (if any).
 */
fun (() -> Unit).withTrace(snapshot: TraceSnapshot = captureTraceSnapshot()): () -> Unit {
    val original = this
    return { snapshot.withTraceSnapshot { original() } }
}

/**
 * Wrap a callback so that when it runs, logs remain attached to [snapshot]'s trace/span (if any).
 */
fun <A> ((A) -> Unit).withTrace(snapshot: TraceSnapshot = captureTraceSnapshot()): (A) -> Unit {
    val original = this
    return { a -> snapshot.withTraceSnapshot { original(a) } }
}

/**
 * Wrap a callback so that when it runs, logs remain attached to [snapshot]'s trace/span (if any).
 */
fun <A, B> ((A, B) -> Unit).withTrace(snapshot: TraceSnapshot = captureTraceSnapshot()): (A, B) -> Unit {
    val original = this
    return { a, b -> snapshot.withTraceSnapshot { original(a, b) } }
}
