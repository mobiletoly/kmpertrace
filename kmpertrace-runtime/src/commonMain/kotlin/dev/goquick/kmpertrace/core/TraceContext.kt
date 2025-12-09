package dev.goquick.kmpertrace.core

import kotlin.coroutines.CoroutineContext

/**
 * CoroutineContext element holding the active span and trace identifiers.
 */
data class TraceContext(
    val traceId: String,
    val spanId: String,
    val parentSpanId: String?,
    val spanName: String,
    val attributes: Map<String, String> = emptyMap(),
    val sourceComponent: String? = null,
    val sourceOperation: String? = null,
    val sourceLocationHint: String? = null
) : CoroutineContext.Element {

    companion object Key : CoroutineContext.Key<TraceContext>

    override val key: CoroutineContext.Key<*> get() = Key
}
