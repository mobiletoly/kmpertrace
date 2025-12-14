package dev.goquick.kmpertrace.trace

import kotlin.coroutines.CoroutineContext

// On JS/Wasm there is no ThreadLocal; we stash the binding flag directly inside the coroutine context.
private class LoggingBindingElement(val value: LoggingBinding) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<LoggingBindingElement>
    override val key: CoroutineContext.Key<*> get() = Key
}

private var currentBinding: LoggingBinding = LoggingBinding.Unbound // JS/Wasm is single-threaded, so a plain var works

internal actual object LoggingBindingStorage {
    actual fun get(): LoggingBinding = currentBinding
    actual fun set(value: LoggingBinding) {
        currentBinding = value
    }

    actual fun element(value: LoggingBinding): CoroutineContext = LoggingBindingElement(value) // store flag in coroutine context on JS/Wasm
}
