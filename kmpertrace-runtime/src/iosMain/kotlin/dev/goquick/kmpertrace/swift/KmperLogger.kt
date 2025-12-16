package dev.goquick.kmpertrace.swift

import dev.goquick.kmpertrace.log.Log
import dev.goquick.kmpertrace.log.LogContext

/**
 * Swift-friendly, component-bound logger.
 *
 * This removes boilerplate at Swift call sites (no Kotlin lambdas; no repeated component strings).
 */
class KmperLogger internal constructor(
    private val component: String,
    private val operation: String? = null
) {
    /**
     * Returns a new logger with the same component and the provided [operation].
     */
    fun withOperation(operation: String): KmperLogger = KmperLogger(component = component, operation = operation)

    fun v(message: String) = context().v { message }
    fun v(operation: String, message: String) = context(operation).v { message }

    fun d(message: String) = context().d { message }
    fun d(operation: String, message: String) = context(operation).d { message }

    fun i(message: String) = context().i { message }
    fun i(operation: String, message: String) = context(operation).i { message }

    fun w(message: String) = context().w { message }
    fun w(operation: String, message: String) = context(operation).w { message }

    fun e(message: String) = context().e { message }
    fun e(operation: String, message: String) = context(operation).e { message }

    private fun context(overrideOperation: String? = null): LogContext {
        val base = Log.forComponent(component)
        val op = overrideOperation ?: operation
        return if (op.isNullOrBlank()) base else base.withOperation(op)
    }
}

/**
 * Swift-friendly logger that automatically installs a [KmperTraceSnapshot] (when present) for each log call.
 *
 * Useful when Swift code runs in callbacks/queues where trace context would otherwise be missing.
 */
class KmperSnapshotLogger internal constructor(
    private val logger: KmperLogger,
    var snapshot: KmperTraceSnapshot? = null
) {
    fun withOperation(operation: String): KmperSnapshotLogger =
        KmperSnapshotLogger(logger = logger.withOperation(operation), snapshot = snapshot)

    fun bind(snapshot: KmperTraceSnapshot?): KmperSnapshotLogger {
        this.snapshot = snapshot
        return this
    }

    fun v(message: String) = withSnapshot { logger.v(message) }
    fun v(operation: String, message: String) = withSnapshot { logger.v(operation, message) }

    fun d(message: String) = withSnapshot { logger.d(message) }
    fun d(operation: String, message: String) = withSnapshot { logger.d(operation, message) }

    fun i(message: String) = withSnapshot { logger.i(message) }
    fun i(operation: String, message: String) = withSnapshot { logger.i(operation, message) }

    fun w(message: String) = withSnapshot { logger.w(message) }
    fun w(operation: String, message: String) = withSnapshot { logger.w(operation, message) }

    fun e(message: String) = withSnapshot { logger.e(message) }
    fun e(operation: String, message: String) = withSnapshot { logger.e(operation, message) }

    private fun withSnapshot(block: () -> Unit) {
        val snap = snapshot
        if (snap == null) {
            block()
        } else {
            snap.with(block)
        }
    }
}

