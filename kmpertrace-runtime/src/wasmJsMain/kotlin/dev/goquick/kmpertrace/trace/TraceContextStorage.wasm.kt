package dev.goquick.kmpertrace.trace

import dev.goquick.kmpertrace.core.TraceContext
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

// JS/Wasm cannot rely on ThreadLocal; we intercept continuations to re-install the captured trace/binding on resume.
private class TraceContextElement(
    private val traceValue: TraceContext?,
    private val downstream: ContinuationInterceptor?
) : CoroutineContext.Element, ContinuationInterceptor {
    override val key: CoroutineContext.Key<*> get() = ContinuationInterceptor

    override fun <T> interceptContinuation(continuation: kotlin.coroutines.Continuation<T>): kotlin.coroutines.Continuation<T> =
        downstream?.interceptContinuation(TraceContinuation(continuation, traceValue))
            ?: TraceContinuation(continuation, traceValue)

    override fun releaseInterceptedContinuation(continuation: kotlin.coroutines.Continuation<*>) {
        downstream?.releaseInterceptedContinuation(continuation)
    }
}

private class TraceContinuation<T>(
    private val delegate: kotlin.coroutines.Continuation<T>,
    private val traceValue: TraceContext?
) : kotlin.coroutines.Continuation<T> {
    override val context: CoroutineContext get() = delegate.context

    override fun resumeWith(result: Result<T>) {
        val previousTrace = currentTraceContext
        val previousBinding = LoggingBindingStorage.get()

        currentTraceContext = traceValue
        LoggingBindingStorage.set(LoggingBinding.BindToSpan)
        try {
            delegate.resumeWith(result)
        } finally {
            currentTraceContext = previousTrace
            LoggingBindingStorage.set(previousBinding)
        }
    }
}

private var currentTraceContext: TraceContext? = null // JS/Wasm is single-threaded, so a top-level var is enough

actual object TraceContextStorage {
    actual fun get(): TraceContext? = currentTraceContext
    actual fun set(value: TraceContext?) {
        currentTraceContext = value
    }

    actual fun element(
        value: TraceContext?,
        downstream: ContinuationInterceptor?
    ): CoroutineContext = TraceContextElement(value, downstream) // capture value and re-install it on continuation resume
}
