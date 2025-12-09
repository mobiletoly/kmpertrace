@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

package dev.goquick.kmpertrace.trace

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ThreadContextElement

// per-thread flag for binding logs to spans
private val bindingLocal: ThreadLocal<LoggingBinding> =
    ThreadLocal.withInitial { LoggingBinding.Unbound }

// ThreadContextElement moves the binding flag across coroutine resumption
// by saving/restoring our ThreadLocal.
@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
private class LoggingBindingElement(private val value: LoggingBinding) :
    ThreadContextElement<LoggingBinding>, CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<LoggingBindingElement>

    override val key: CoroutineContext.Key<*> get() = Key

    override fun updateThreadContext(context: CoroutineContext): LoggingBinding {
        val previous = bindingLocal.get()
        bindingLocal.set(value) // install span-binding flag before coroutine resumes on this thread
        return previous
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: LoggingBinding) {
        bindingLocal.set(oldState) // put back whatever binding was there before
    }
}

actual object LoggingBindingStorage {
    actual fun get(): LoggingBinding = bindingLocal.get()

    actual fun set(value: LoggingBinding) {
        bindingLocal.set(value)
    }

    actual fun element(value: LoggingBinding): CoroutineContext = LoggingBindingElement(value)
}
