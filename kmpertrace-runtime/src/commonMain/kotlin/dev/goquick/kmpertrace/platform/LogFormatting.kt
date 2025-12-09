package dev.goquick.kmpertrace.platform

import dev.goquick.kmpertrace.core.EventKind
import dev.goquick.kmpertrace.core.LogEvent
import dev.goquick.kmpertrace.trace.defaultSpanMessage

/**
 * Build the structured log line for platform backends.
 *
 * By default a human-friendly prefix (timestamp/level/logger/message) is included.
 * On Android we skip the prefix to avoid duplicating Logcat’s own timestamp/level.
 */
internal fun formatLogLine(event: LogEvent, includeHumanPrefix: Boolean = true): String {
    val hasTrace = event.traceId != null
    val traceId = event.traceId ?: "0"
    val spanId = event.spanId ?: "0"
    val parentSpanId = event.parentSpanId ?: if (hasTrace) "-" else "0"
    val spanName = event.spanName
    val durationMs = event.durationMs
    val service = event.serviceName
    val environment = event.environment
    val threadName = event.threadName
    val srcComponent = event.sourceComponent
    val srcOperation = event.sourceOperation
    val srcHint = event.sourceLocationHint
    val srcFile = event.sourceFile
    val srcLine = event.sourceLine
    val srcFunction = event.sourceFunction
    val spanLabel = event.spanName ?: event.sourceLocationHint ?: event.sourceOperation ?: event.sourceComponent
    val defaultSpanMsg = defaultSpanMessage(event.eventKind, spanLabel)
    val humanMessage = if (event.eventKind == EventKind.LOG) {
        event.message
    } else {
        event.message.ifBlank { defaultSpanMsg.orEmpty() }
    }
    val headSnippet = headValue(humanMessage)
    val structured = buildString {
        append("ts=").append(event.timestamp)
        append(" lvl=").append(event.level.name.lowercase())
        if (hasTrace || traceId != "0") {
            append(" trace=").append(traceId)
            append(" span=").append(spanId)
            if (parentSpanId != "0" && parentSpanId != "-") append(" parent=").append(parentSpanId)
        }
        if (event.eventKind != EventKind.LOG) {
            append(" ev=").append(event.eventKind.name)
        }
        if (spanLabel != null && event.eventKind != EventKind.LOG) {
            append(" name=").append(quote(spanLabel))
        }
        if (durationMs != null && event.eventKind == EventKind.SPAN_END && durationMs != 0L) {
            append(" dur=").append(durationMs)
        }
        if (headSnippet.isNotBlank()) append(" head=").append(quote(headSnippet))

        val srcCombined = when {
            srcComponent != null && srcOperation != null -> "${srcComponent}/${srcOperation}"
            srcComponent != null -> srcComponent
            srcHint != null -> srcHint
            else -> null
        }
        if (srcCombined != null) append(" src=").append(srcCombined)
        if (event.loggerName.isNotBlank() && (srcCombined == null || event.loggerName != srcCombined)) {
            append(" log=").append(event.loggerName)
        }
        if (!service.isNullOrBlank() && service != "-") append(" svc=").append(service)
        if (!environment.isNullOrBlank() && environment != "-") append(" env=").append(environment)
        if (!threadName.isNullOrBlank() && threadName != "unknown") append(" thread=").append(quote(threadName))
        if (srcFile != null) append(" file=").append(srcFile)
        if (srcLine != null) append(" line=").append(srcLine)
        if (srcFunction != null) append(" fn=").append(quote(srcFunction))
        event.attributes.forEach { (key, value) ->
            append(' ').append(key).append('=').append(quote(value))
        }
        event.throwable?.let { throwable ->
            append(" throwable=").append(quote(throwable::class.simpleName ?: "Throwable"))
            val rawStack = throwable.stackTraceToString().replace("\r", "").trimEnd('\n')
            val normalizedStack = rawStack
                .lineSequence()
                .mapIndexed { idx, line ->
                    val trimmed = line.trimStart()
                    if (idx == 0) trimmed else "    $trimmed" // header stays flush; frames indented
                }
                .joinToString("\n")
            val stack = "\n$normalizedStack\n" // start on a new line and end with one so closing quote sits on its own line
            append(" stack_trace=").append(quote(stack)) // keep stack_trace last; allow real newlines inside quotes
        }
    }

    val structuredWrapped = "|{ $structured }|"

    if (!includeHumanPrefix) {
        return structuredWrapped
    }

    val human = buildString {
        append(event.timestamp)
        append(' ')
        append(event.level.name)
        append(' ')
        append(event.loggerName.ifBlank { "KmperTrace" })
        if (humanMessage.isNotBlank()) {
            append(' ')
            append(humanMessage)
        }
    }

    return "$human $structuredWrapped"
}

private fun quote(value: String): String = buildString {
    append('"')
    value.forEach { ch ->
        if (ch == '"') append("\\\"") else append(ch)
    }
    append('"')
}

private fun headValue(text: String, maxLen: Int = 15): String {
    val trimmed = text.trim()
    return if (trimmed.length <= maxLen) trimmed else trimmed.substring(0, maxLen)
}
