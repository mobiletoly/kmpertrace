package dev.goquick.kmpertrace.trace

/**
 * Internal toggle describing whether log records should bind to the current span.
 */
internal enum class LoggingBinding {
    Unbound,
    BindToSpan
}

/**
 * Platform storage for the current logging binding mode.
 *
 * This mirrors [TraceContextStorage] so we can decide at emission time whether to attach
 * trace/span IDs to log records. Each platform chooses the same propagation strategy as
 * TraceContextStorage (ThreadLocal + ThreadContextElement, or coroutine-context storage).
 */
internal expect object LoggingBindingStorage {
    /**
     * Current binding mode in this execution context.
     */
    fun get(): LoggingBinding

    /**
     * Set the binding mode for subsequent log records.
     */
    fun set(value: LoggingBinding)

    /**
     * Coroutine context element that propagates [LoggingBinding] across suspending boundaries.
     */
    fun element(value: LoggingBinding): kotlin.coroutines.CoroutineContext
}
