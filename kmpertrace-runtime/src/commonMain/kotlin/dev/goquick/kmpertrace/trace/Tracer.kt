package dev.goquick.kmpertrace.trace

import dev.goquick.kmpertrace.core.EventKind
import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.core.LogEvent
import dev.goquick.kmpertrace.core.SpanKind
import dev.goquick.kmpertrace.core.TraceContext
import dev.goquick.kmpertrace.log.Log
import dev.goquick.kmpertrace.log.LoggerConfig
import dev.goquick.kmpertrace.log.currentThreadNameOrNull
import dev.goquick.kmpertrace.log.dispatchEvent
import dev.goquick.kmpertrace.log.defaultLoggerName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.ContinuationInterceptor
import kotlin.random.Random
import kotlin.time.Clock

// Propagation pipeline in one place:
// 1) Tracer.span creates a TraceContext and installs both TraceContextStorage.element(...) and
//    LoggingBindingStorage.element(BindToSpan) in the coroutine context.
// 2) Platform-specific TraceContextStorage/LoggingBindingStorage keep those values alive across
//    thread hops/suspends (ThreadLocal+ThreadContextElement on JVM/Android, continuation wrapper on iOS,
//    direct coroutine context storage on JS/Wasm).
// 3) Log.logInternal reads LoggingBindingStorage to decide whether to attach trace/span IDs.

/**
 * Span-scoped helper that can emit logs with the current trace context.
 */
interface SpanScope {
    /**
     * Active trace/span metadata available to the block.
     */
    val traceContext: TraceContext

    /**
     * Emit a log event associated with the current span.
     */
    fun log(level: Level = Level.INFO, message: () -> String)
}

/**
 * Tracer capable of creating spans and propagating trace context.
 */
interface Tracer {
    /**
     * Execute [block] inside a new span, binding logs to it and propagating trace metadata via [TraceContext].
     */
    suspend fun <T> span(
        name: String,
        kind: SpanKind = SpanKind.INTERNAL,
        attributes: Map<String, String> = emptyMap(),
        sourceComponent: String? = null,
        sourceOperation: String? = null,
        sourceLocationHint: String? = null,
        block: suspend SpanScope.() -> T
    ): T
}

/**
 * Default KmperTrace Tracer implementation.
 */
object KmperTracer : Tracer {
    override suspend fun <T> span(
        name: String,
        kind: SpanKind,
        attributes: Map<String, String>,
        sourceComponent: String?,
        sourceOperation: String?,
        sourceLocationHint: String?,
        block: suspend SpanScope.() -> T
    ): T {
        val parentContext = currentCoroutineContext()[TraceContext]
        val traceId = parentContext?.traceId ?: generateTraceId() // reuse trace when nested, otherwise start new
        val parentSpanId = parentContext?.spanId
        val spanId = generateSpanId()

        val newContext = TraceContext(
            traceId = traceId,
            spanId = spanId,
            parentSpanId = parentSpanId,
            spanName = name,
            attributes = attributes,
            sourceComponent = sourceComponent ?: parentContext?.sourceComponent,
            sourceOperation = sourceOperation ?: parentContext?.sourceOperation,
            sourceLocationHint = sourceLocationHint ?: parentContext?.sourceLocationHint
        )

        emitSpanStart(newContext, kind)
        val startInstant = Clock.System.now()

        val parentInterceptor = currentCoroutineContext()[ContinuationInterceptor]
        val previousTrace = TraceContextStorage.get()
        val previousBinding = LoggingBindingStorage.get()

        return withContext(
            currentCoroutineContext() +
                newContext + // surface TraceContext via CoroutineContext lookup
                TraceContextStorage.element(newContext, parentInterceptor) + // install platform-specific propagation
                LoggingBindingStorage.element(LoggingBinding.BindToSpan) // ensure Log binds events to this span
        ) {
            // Ensure the current thread sees the span context immediately, and restore afterward.
            TraceContextStorage.set(newContext)
            LoggingBindingStorage.set(LoggingBinding.BindToSpan)
            val scope = object : SpanScope {
                override val traceContext: TraceContext = newContext
                override fun log(level: Level, message: () -> String) {
                    Log.logInternal(level, null, null, message) // log using current binding (span)
                }
            }

            var error: Throwable? = null
            val result: T = try {
                scope.block()
            } catch (t: Throwable) {
                error = t
                throw t
            } finally {
                emitSpanEnd(newContext, startInstant, error) // always close the span, even on error
                TraceContextStorage.set(previousTrace)
                LoggingBindingStorage.set(previousBinding)
            }

            result
        }
    }
}

/**
 * Convenience helper to run a suspending block inside a new span.
 */
suspend fun <T> traceSpan(
    name: String,
    kind: SpanKind = SpanKind.INTERNAL,
    attributes: Map<String, String> = emptyMap(),
    block: suspend SpanScope.() -> T
): T = KmperTracer.span(name, kind, attributes, null, null, null, block)

/**
 * Convenience helper to run a suspending block inside a new span with component/operation metadata.
 */
suspend fun <T> traceSpan(
    component: String,
    operation: String,
    attributes: Map<String, String> = emptyMap(),
    block: suspend SpanScope.() -> T
): T = KmperTracer.span(
    name = "$component.$operation",
    attributes = attributes,
    sourceComponent = component,
    sourceOperation = operation,
    sourceLocationHint = "$component.$operation",
    block = block
)

/**
 * Variant of [traceSpan] that derives the component name from [TComponent].
 */
suspend inline fun <reified TComponent, T> traceSpanForClass(
    operation: String,
    attributes: Map<String, String> = emptyMap(),
    noinline block: suspend SpanScope.() -> T
): T = traceSpan(
    component = TComponent::class.simpleName ?: TComponent::class.toString(),
    operation = operation,
    attributes = attributes,
    block = block
)

/**
 * Create a child span in the current trace without suspending. Emits SPAN_START/END around [block].
 * Useful for synchronous code that still wants a nested span node.
 */
fun <T> inlineSpan(
    component: String,
    operation: String,
    attributes: Map<String, String> = emptyMap(),
    block: () -> T
): T {
    val parent = TraceContextStorage.get()
    val ctx = TraceContext(
        traceId = parent?.traceId ?: generateTraceId(),
        spanId = generateSpanId(),
        parentSpanId = parent?.spanId,
        spanName = "$component.$operation",
        sourceComponent = component,
        sourceOperation = operation,
        sourceLocationHint = "$component.$operation",
        attributes = attributes
    )
    val startInstant = Clock.System.now()
    emitSpanStart(ctx, SpanKind.INTERNAL)
    val prevBinding = LoggingBindingStorage.get()
    TraceContextStorage.set(ctx)
    LoggingBindingStorage.set(LoggingBinding.BindToSpan)
    return try {
        block()
    } finally {
        emitSpanEnd(ctx, startInstant, null)
        TraceContextStorage.set(parent)
        LoggingBindingStorage.set(prevBinding)
    }
}

private fun emitSpanStart(context: TraceContext, kind: SpanKind) {
    val now = Clock.System.now()
    val defaultMsg = defaultSpanMessage(EventKind.SPAN_START, context.spanName)
    val event = LogEvent(
        timestamp = now,
        level = Level.INFO,
        loggerName = context.sourceComponent ?: defaultLoggerName(),
        message = defaultMsg.orEmpty(),
        traceId = context.traceId,
        spanId = context.spanId,
        parentSpanId = context.parentSpanId,
        eventKind = EventKind.SPAN_START,
        spanName = context.spanName,
        durationMs = 0L,
        threadName = currentThreadNameOrNull(),
        serviceName = LoggerConfig.serviceName,
        environment = LoggerConfig.environment,
        sourceComponent = context.sourceComponent,
        sourceOperation = context.sourceOperation,
        sourceLocationHint = context.sourceLocationHint,
        attributes = buildMap {
            if (kind != SpanKind.INTERNAL) {
                put("span_kind", kind.name.lowercase())
            }
        }
    )
    dispatchEvent(event)
}

private fun emitSpanEnd(context: TraceContext, startInstant: kotlin.time.Instant, error: Throwable?) {
    val endInstant = Clock.System.now()
    val durationMs = endInstant.toEpochMilliseconds() - startInstant.toEpochMilliseconds()
    val defaultMsg = defaultSpanMessage(EventKind.SPAN_END, context.spanName)
    val baseAttributes = mutableMapOf<String, String>()
    if (error != null) {
        baseAttributes["status"] = "ERROR"
        baseAttributes["error_type"] = error::class.simpleName ?: "Unknown"
        baseAttributes["error_message"] = error.message ?: ""
    }

    val mergedAttributes = buildMap {
        putAll(context.attributes)
        putAll(baseAttributes)
    }

    val event = LogEvent(
        timestamp = endInstant,
        level = if (error != null) Level.ERROR else Level.INFO,
        loggerName = context.sourceComponent ?: defaultLoggerName(),
        message = defaultMsg.orEmpty(),
        traceId = context.traceId,
        spanId = context.spanId,
        parentSpanId = context.parentSpanId,
        eventKind = EventKind.SPAN_END,
        spanName = context.spanName,
        durationMs = durationMs,
        threadName = currentThreadNameOrNull(),
        serviceName = LoggerConfig.serviceName,
        environment = LoggerConfig.environment,
        sourceComponent = context.sourceComponent,
        sourceOperation = context.sourceOperation,
        sourceLocationHint = context.sourceLocationHint,
        attributes = mergedAttributes,
        throwable = error
    )
    dispatchEvent(event)
}

// Use 64-bit trace IDs (16 hex chars) instead of 128-bit to shorten logs.
private fun generateTraceId(): String = generateHexId(8)

private fun generateSpanId(): String = generateHexId(8)

private fun generateHexId(bytes: Int): String {
    val data = Random.nextBytes(bytes)
    val chars = CharArray(bytes * 2)
    data.forEachIndexed { index, byte ->
        val i = byte.toInt() and 0xFF
        chars[index * 2] = hexDigits[i ushr 4]
        chars[index * 2 + 1] = hexDigits[i and 0x0F]
    }
    return chars.concatToString()
}

private val hexDigits = "0123456789abcdef".toCharArray()
