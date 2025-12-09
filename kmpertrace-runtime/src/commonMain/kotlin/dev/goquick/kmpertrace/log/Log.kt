package dev.goquick.kmpertrace.log

import dev.goquick.kmpertrace.core.EventKind
import dev.goquick.kmpertrace.core.Level
import dev.goquick.kmpertrace.core.LogEvent
import dev.goquick.kmpertrace.core.TraceContext
import dev.goquick.kmpertrace.trace.LoggingBinding
import dev.goquick.kmpertrace.trace.LoggingBindingStorage
import dev.goquick.kmpertrace.trace.TraceContextStorage
import kotlin.time.Clock

/**
 * Static-style logging API producing structured LogEvents.
 */
interface LogContext {
    /**
     * Component name to attach to emitted events (e.g., class or feature).
     */
    val component: String?

    /**
     * Operation name within the component to attach to emitted events.
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
        Log.logInternal(Level.VERBOSE, null, throwable, message, component, operation, buildLocationHint(component, operation))
    }

    /**
     * Debug log bound to this context; skipped when below [LoggerConfig.minLevel].
     */
    fun d(throwable: Throwable? = null, message: () -> String) {
        if (!isLoggable(Level.DEBUG)) return
        Log.logInternal(Level.DEBUG, null, throwable, message, component, operation, buildLocationHint(component, operation))
    }

    /**
     * Info log bound to this context; skipped when below [LoggerConfig.minLevel].
     */
    fun i(throwable: Throwable? = null, message: () -> String) {
        if (!isLoggable(Level.INFO)) return
        Log.logInternal(Level.INFO, null, throwable, message, component, operation, buildLocationHint(component, operation))
    }

    /**
     * Warn log bound to this context; skipped when below [LoggerConfig.minLevel].
     */
    fun w(throwable: Throwable? = null, message: () -> String) {
        if (!isLoggable(Level.WARN)) return
        Log.logInternal(Level.WARN, null, throwable, message, component, operation, buildLocationHint(component, operation))
    }

    /**
     * Error log bound to this context; skipped when below [LoggerConfig.minLevel].
     */
    fun e(throwable: Throwable? = null, message: () -> String) {
        if (!isLoggable(Level.ERROR)) return
        Log.logInternal(Level.ERROR, null, throwable, message, component, operation, buildLocationHint(component, operation))
    }

    /**
     * Assert-level log bound to this context; skipped when below [LoggerConfig.minLevel].
     */
    fun wtf(throwable: Throwable? = null, message: () -> String) {
        if (!isLoggable(Level.ASSERT)) return
        Log.logInternal(Level.ASSERT, null, throwable, message, component, operation, buildLocationHint(component, operation))
    }
}

/**
 * Static logger utility for emitting structured KmperTrace events.
 *
 * When the current coroutine has `LoggingBindingStorage` set to [dev.goquick.kmpertrace.trace.LoggingBinding.BindToSpan],
 * emitted events include trace/span IDs from the active [TraceContext]; otherwise they are unbound.
 */
object Log {

    /**
     * Build a [LogContext] that automatically tags events with the given component.
     */
    fun forComponent(component: String): LogContext = ComponentLogContext(component, null)

    /**
     * Build a [LogContext] using the simple name of [T] as the component.
     */
    inline fun <reified T> forClass(): LogContext = forComponent(T::class.simpleName ?: T::class.toString())

    /**
     * Verbose log; skipped if below [LoggerConfig.minLevel].
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun v(tag: String? = null, throwable: Throwable? = null, noinline message: () -> String) {
        if (!isLoggable(Level.VERBOSE)) return
        logInternal(Level.VERBOSE, tag, throwable, message)
    }

    /**
     * Debug log; skipped if below [LoggerConfig.minLevel].
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun d(tag: String? = null, throwable: Throwable? = null, noinline message: () -> String) {
        if (!isLoggable(Level.DEBUG)) return
        logInternal(Level.DEBUG, tag, throwable, message)
    }

    /**
     * Info log; skipped if below [LoggerConfig.minLevel].
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun i(tag: String? = null, throwable: Throwable? = null, noinline message: () -> String) {
        if (!isLoggable(Level.INFO)) return
        logInternal(Level.INFO, tag, throwable, message)
    }

    /**
     * Warn log; skipped if below [LoggerConfig.minLevel].
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun w(tag: String? = null, throwable: Throwable? = null, noinline message: () -> String) {
        if (!isLoggable(Level.WARN)) return
        logInternal(Level.WARN, tag, throwable, message)
    }

    /**
     * Error log; skipped if below [LoggerConfig.minLevel].
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun e(tag: String? = null, throwable: Throwable? = null, noinline message: () -> String) {
        if (!isLoggable(Level.ERROR)) return
        logInternal(Level.ERROR, tag, throwable, message)
    }

    /**
     * Assert-level log; skipped if below [LoggerConfig.minLevel].
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun wtf(tag: String? = null, throwable: Throwable? = null, noinline message: () -> String) {
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
        val now = Clock.System.now()
        val traceContext = currentTraceContextOrNull()
        val binding = LoggingBindingStorage.get()
        // Only attach trace/span IDs when the current binding instructs us to.
        val boundContext = if (traceContext != null && binding == LoggingBinding.BindToSpan) traceContext else null
        val component = sourceComponent ?: boundContext?.sourceComponent
        val operation = sourceOperation ?: boundContext?.sourceOperation
        val locationHint = sourceLocationHint ?: boundContext?.sourceLocationHint

        val event = LogEvent(
            timestamp = now,
            level = level,
            loggerName = tag ?: component ?: defaultLoggerName(),
            message = message(),
            traceId = boundContext?.traceId,
            spanId = boundContext?.spanId,
            parentSpanId = boundContext?.parentSpanId,
            eventKind = EventKind.LOG,
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

        val backends = LoggerConfig.backends
        if (backends.isEmpty()) return

        dispatchEvent(event)
    }
}

@PublishedApi
internal inline fun isLoggable(level: Level): Boolean {
    val min = LoggerConfig.minLevel
    return level.ordinal >= min.ordinal
}

@PublishedApi
internal fun dispatchEvent(event: LogEvent) {
    if (!LoggerConfig.filter(event)) return
    LoggerConfig.backends.forEach { backend -> backend.log(event) }
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
