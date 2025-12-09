package dev.goquick.kmpertrace.trace

import dev.goquick.kmpertrace.core.TraceContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Propagate the current trace/binding when hopping to a different dispatcher inside a span.
 */
suspend fun <T> withTraceContext(
    dispatcher: CoroutineDispatcher,
    block: suspend () -> T
): T {
    val ctx = currentCoroutineContext()
    val trace: TraceContext? = ctx[TraceContext] ?: TraceContextStorage.get()
    val downstreamInterceptor = dispatcher as? ContinuationInterceptor

    val propagationContext =
        (trace?.let { TraceContextStorage.element(it, downstreamInterceptor) } ?: EmptyCoroutineContext) +
            LoggingBindingStorage.element(LoggingBinding.BindToSpan)

    return withContext(dispatcher + propagationContext) {
        val previousTrace = TraceContextStorage.get()
        val previousBinding = LoggingBindingStorage.get()

        if (trace != null) {
            TraceContextStorage.set(trace)
            LoggingBindingStorage.set(LoggingBinding.BindToSpan)
        }

        try {
            block()
        } finally {
            TraceContextStorage.set(previousTrace)
            LoggingBindingStorage.set(previousBinding)
        }
    }
}
