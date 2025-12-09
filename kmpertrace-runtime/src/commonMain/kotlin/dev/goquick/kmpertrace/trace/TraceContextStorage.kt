package dev.goquick.kmpertrace.trace

import dev.goquick.kmpertrace.core.TraceContext

/**
 * Platform-specific holder for the active [TraceContext].
 *
 * Each platform chooses its own propagation strategy:
 * - JVM/Android: store in a ThreadLocal and use a ThreadContextElement to save/restore across coroutine dispatch.
 * - iOS/Native: store in a ThreadLocal and wrap the continuation to re-install the value when the coroutine resumes.
 * - JS/Wasm: no ThreadLocal support; stash the value directly in the coroutine context.
 */
internal expect object TraceContextStorage {
    fun get(): TraceContext?
    fun set(value: TraceContext?)
    fun element(
        value: TraceContext?,
        downstream: kotlin.coroutines.ContinuationInterceptor? = null // existing interceptor chain to preserve
    ): kotlin.coroutines.CoroutineContext
}
