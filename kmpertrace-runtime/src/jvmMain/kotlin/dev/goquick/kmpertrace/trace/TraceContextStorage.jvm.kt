package dev.goquick.kmpertrace.trace

import dev.goquick.kmpertrace.core.TraceContext
import kotlinx.coroutines.asContextElement
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

private val traceLocal: ThreadLocal<TraceContext?> = ThreadLocal() // per-thread active span context

actual object TraceContextStorage {
    actual fun get(): TraceContext? = traceLocal.get()
    actual fun set(value: TraceContext?) {
        traceLocal.set(value)
    }

    actual fun element(
        value: TraceContext?,
        downstream: ContinuationInterceptor?
    ): CoroutineContext = traceLocal.asContextElement(value) // coroutines restore this ThreadLocal across suspends
}
