package dev.goquick.kmpertrace.log

import dev.goquick.kmpertrace.core.LogRecordKind
import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.core.StructuredLogRecord
import dev.goquick.kmpertrace.core.TraceContext
import dev.goquick.kmpertrace.trace.LoggingBinding
import dev.goquick.kmpertrace.trace.LoggingBindingStorage
import dev.goquick.kmpertrace.trace.TraceTrigger
import dev.goquick.kmpertrace.trace.TraceContextStorage
import dev.goquick.kmpertrace.trace.traceSpan
import dev.goquick.kmpertrace.platform.renderLogLine
import kotlin.time.Clock

/**
 * Static-style logging API producing structured KmperTrace log records.
 */
interface LogContext {
    /**
     * Component name to attach to emitted log records (e.g., class or feature).
     */
    val component: String?

    /**
     * Operation name within the component to attach to emitted log records.
     */
    val operation: String?

    /**
     * Returns a new [LogContext] that keeps the same component but overrides the operation.
     */
    fun withOperation(operation: String): LogContext

    /**
     * Verbose log bound to this context; skipped when below [LoggerConfig.minLevel].
     */
    fun v(throwable: Throwable? = null, message: () -> String) {
        if (!isLoggable(Level.VERBOSE)) return
        Log.logInternal(
            Level.VERBOSE,
            null,
            throwable,
            message,
            component,
            operation,
            null
        )
    }

    /**
     * Debug log bound to this context; skipped when below [LoggerConfig.minLevel].
     */
    fun d(throwable: Throwable? = null, message: () -> String) {
        if (!isLoggable(Level.DEBUG)) return
        Log.logInternal(
            Level.DEBUG,
            null,
            throwable,
            message,
            component,
            operation,
            null
        )
    }

    /**
     * Info log bound to this context; skipped when below [LoggerConfig.minLevel].
     */
    fun i(throwable: Throwable? = null, message: () -> String) {
        if (!isLoggable(Level.INFO)) return
        Log.logInternal(
            Level.INFO,
            null,
            throwable,
            message,
            component,
            operation,
            null
        )
    }

    /**
     * Warn log bound to this context; skipped when below [LoggerConfig.minLevel].
     */
    fun w(throwable: Throwable? = null, message: () -> String) {
        if (!isLoggable(Level.WARN)) return
        Log.logInternal(
            Level.WARN,
            null,
            throwable,
            message,
            component,
            operation,
            null
        )
    }

    /**
     * Error log bound to this context; skipped when below [LoggerConfig.minLevel].
     */
    fun e(throwable: Throwable? = null, message: () -> String) {
        if (!isLoggable(Level.ERROR)) return
        Log.logInternal(
            Level.ERROR,
            null,
            throwable,
            message,
            component,
            operation,
            null
        )
    }

    /**
     * Assert-level log bound to this context; skipped when below [LoggerConfig.minLevel].
     */
    fun wtf(throwable: Throwable? = null, message: () -> String) {
        if (!isLoggable(Level.ASSERT)) return
        Log.logInternal(
            Level.ASSERT,
            null,
            throwable,
            message,
            component,
            operation,
            null
        )
    }
}

/**
 * Run [block] inside a span named by this logger's component and the provided [operation].
 * The span is created via [traceSpan] and log calls inside the block inherit trace/span IDs.
 */
suspend inline fun <T> LogContext.span(
    operation: String,
    attributes: Map<String, String> = emptyMap(),
    crossinline block: suspend () -> T
): T = traceSpan(
    component = component ?: defaultLoggerName(),
    operation = operation,
    attributes = attributes
) {
    block()
}

/**
 * Run [block] inside a span intended to represent a user/system journey.
 *
 * If there is no active trace, this becomes a root span (new trace). If called from within an existing
 * trace/span, this becomes a child span in the same trace.
 *
 * The span records an `a:trigger` attribute and emits one INFO milestone log at the start so that UIs can
 * show the trigger even when span attributes are hidden.
 */
suspend inline fun <T> LogContext.journey(
    operation: String,
    trigger: TraceTrigger,
    attributes: Map<String, String> = emptyMap(),
    crossinline block: suspend () -> T
): T {
    val mergedAttributes =
        buildMap {
            putAll(attributes)
            put("trigger", trigger.value)
        }

    return traceSpan(
        component = component ?: defaultLoggerName(),
        operation = operation,
        attributes = mergedAttributes
    ) {
        this@journey.i { "journey started (trigger=${trigger.value})" }
        block()
    }
}

/**
 * Run [block] inside a lightweight child span (inline span) using the current trace/span, without requiring suspend.
 * Useful for synchronous code that still wants a nested span node.
 */
inline fun <T> LogContext.inlineSpan(
    operation: String,
    attributes: Map<String, String> = emptyMap(),
    crossinline block: () -> T
): T = dev.goquick.kmpertrace.trace.inlineSpan(
    component = component ?: defaultLoggerName(),
    operation = operation,
    attributes = attributes
) {
    block()
}

/**
 * Static logger utility for emitting structured KmperTrace log records.
 *
 * When the current coroutine has `LoggingBindingStorage` set to [dev.goquick.kmpertrace.trace.LoggingBinding.BindToSpan],
 * emitted log records include trace/span IDs from the active [TraceContext]; otherwise they are unbound.
 */
object Log {

    /**
     * Build a [LogContext] that automatically tags log records with the given component.
     */
    fun forComponent(component: String): LogContext = ComponentLogContext(component, null)

    /**
     * Build a [LogContext] using the simple name of [T] as the component.
     */
    inline fun <reified T> forClass(): LogContext =
        forComponent(T::class.simpleName ?: T::class.toString())

    /**
     * Verbose log; skipped if below [LoggerConfig.minLevel].
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun v(
        tag: String? = null,
        throwable: Throwable? = null,
        noinline message: () -> String
    ) {
        if (!isLoggable(Level.VERBOSE)) return
        logInternal(Level.VERBOSE, tag, throwable, message)
    }

    /**
     * Debug log; skipped if below [LoggerConfig.minLevel].
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun d(
        tag: String? = null,
        throwable: Throwable? = null,
        noinline message: () -> String
    ) {
        if (!isLoggable(Level.DEBUG)) return
        logInternal(Level.DEBUG, tag, throwable, message)
    }

    /**
     * Info log; skipped if below [LoggerConfig.minLevel].
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun i(
        tag: String? = null,
        throwable: Throwable? = null,
        noinline message: () -> String
    ) {
        if (!isLoggable(Level.INFO)) return
        logInternal(Level.INFO, tag, throwable, message)
    }

    /**
     * Warn log; skipped if below [LoggerConfig.minLevel].
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun w(
        tag: String? = null,
        throwable: Throwable? = null,
        noinline message: () -> String
    ) {
        if (!isLoggable(Level.WARN)) return
        logInternal(Level.WARN, tag, throwable, message)
    }

    /**
     * Error log; skipped if below [LoggerConfig.minLevel].
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun e(
        tag: String? = null,
        throwable: Throwable? = null,
        noinline message: () -> String
    ) {
        if (!isLoggable(Level.ERROR)) return
        logInternal(Level.ERROR, tag, throwable, message)
    }

    /**
     * Assert-level log; skipped if below [LoggerConfig.minLevel].
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun wtf(
        tag: String? = null,
        throwable: Throwable? = null,
        noinline message: () -> String
    ) {
        if (!isLoggable(Level.ASSERT)) return
        logInternal(Level.ASSERT, tag, throwable, message)
    }

    @PublishedApi
    internal fun logInternal(
        level: Level,
        tag: String?,
        throwable: Throwable?,
        message: () -> String,
        sourceComponent: String? = null,
        sourceOperation: String? = null,
        sourceLocationHint: String? = null
    ) {
        if (!isLoggable(level)) return
        if (LoggerConfig.sinks.isEmpty()) return

        val now = Clock.System.now()
        val traceContext = currentTraceContextOrNull()
        val binding = LoggingBindingStorage.get()
        // Only attach trace/span IDs when the current binding instructs us to.
        val boundContext =
            if (traceContext != null && binding == LoggingBinding.BindToSpan) traceContext else null
        val component = sourceComponent ?: boundContext?.sourceComponent
        val operation = sourceOperation ?: boundContext?.sourceOperation
        val locationHint =
            when {
                sourceLocationHint != null -> sourceLocationHint
                sourceComponent != null || sourceOperation != null -> buildLocationHint(
                    component,
                    operation
                )

                else -> boundContext?.sourceLocationHint
            }

        val record = StructuredLogRecord(
            timestamp = now,
            level = level,
            loggerName = tag ?: component ?: defaultLoggerName(),
            message = message(),
            traceId = boundContext?.traceId,
            spanId = boundContext?.spanId,
            parentSpanId = boundContext?.parentSpanId,
            logRecordKind = LogRecordKind.LOG,
            spanName = boundContext?.spanName,
            durationMs = null,
            threadName = currentThreadNameOrNull(),
            serviceName = LoggerConfig.serviceName,
            environment = LoggerConfig.environment,
            sourceComponent = component,
            sourceOperation = operation,
            sourceLocationHint = locationHint,
            attributes = emptyMap(),
            throwable = throwable
        )

        dispatchRecord(record)
    }
}

@PublishedApi
internal fun isLoggable(level: Level): Boolean {
    val min = LoggerConfig.minLevel
    return level.ordinal >= min.ordinal
}

@PublishedApi
internal fun dispatchRecord(record: StructuredLogRecord) {
    val sinks = LoggerConfig.sinks
    if (sinks.isEmpty()) return

    val rendered = renderLogLine(record)
    val renderedRecord = LogRecord(
        timestamp = record.timestamp,
        level = record.level,
        tag = record.loggerName.ifBlank { defaultLoggerName() },
        message = rendered.humanMessage,
        line = rendered.line,
        structuredSuffix = rendered.structuredSuffix
    )

    if (!LoggerConfig.filter(renderedRecord)) return
    sinks.forEach { sink -> sink.emit(renderedRecord) }
}

@PublishedApi
internal fun currentTraceContextOrNull(): TraceContext? =
    TraceContextStorage.get()

@PublishedApi
internal fun defaultLoggerName(): String = "KmperTrace"

@PublishedApi
internal expect fun currentThreadNameOrNull(): String?

@PublishedApi
internal fun buildLocationHint(component: String?, operation: String?): String? =
    when {
        component != null && operation != null -> "$component.$operation"
        component != null -> component
        else -> null

    }

private data class ComponentLogContext(
    override val component: String?,
    override val operation: String?
) : LogContext {
    override fun withOperation(operation: String): LogContext = copy(operation = operation)
}
