package dev.goquick.kmpertrace.trace

import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.ThreadLocal

private class LoggingBindingElement(private val value: LoggingBinding) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<LoggingBindingElement>
    override val key: CoroutineContext.Key<*> get() = Key
}

@ThreadLocal
private var currentBinding: LoggingBinding = LoggingBinding.Unbound // one binding flag per native thread
// Binding is reinstalled by TraceContextStorage's continuation wrapper when resuming.

actual object LoggingBindingStorage {
    actual fun get(): LoggingBinding = currentBinding
    actual fun set(value: LoggingBinding) {
        currentBinding = value
    }

    actual fun element(value: LoggingBinding): CoroutineContext = LoggingBindingElement(value)
}
