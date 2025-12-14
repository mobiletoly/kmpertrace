package dev.goquick.kmpertrace.platform

import dev.goquick.kmpertrace.core.LogRecordKind
import dev.goquick.kmpertrace.core.StructuredLogRecord
import dev.goquick.kmpertrace.trace.defaultSpanMessage

internal data class RenderedLogLine(
    val humanMessage: String,
    val structuredSuffix: String,
    val line: String
)

internal fun renderLogLine(record: StructuredLogRecord): RenderedLogLine {
    val (humanMessage, structuredSuffix) = renderHumanMessageAndStructuredSuffix(record)
    val human = buildString {
        append(record.timestamp)
        append(' ')
        append(record.level.name)
        append(' ')
        append(record.loggerName.ifBlank { "KmperTrace" })
        if (humanMessage.isNotBlank()) {
            append(' ')
            append(humanMessage)
        }
    }
    return RenderedLogLine(
        humanMessage = humanMessage,
        structuredSuffix = structuredSuffix,
        line = "$human $structuredSuffix"
    )
}

/**
 * Build the structured log line for platform backends.
 *
 * By default a human-friendly prefix (timestamp/level/logger/message) is included.
 * On Android we skip the prefix to avoid duplicating Logcat’s own timestamp/level.
 */
internal fun formatLogLine(record: StructuredLogRecord, includeHumanPrefix: Boolean = true): String {
    val rendered = renderLogLine(record)
    return if (includeHumanPrefix) rendered.line else rendered.structuredSuffix
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

private fun renderHumanMessageAndStructuredSuffix(record: StructuredLogRecord): Pair<String, String> {
    val hasTrace = record.traceId != null
    val traceId = record.traceId ?: "0"
    val spanId = record.spanId ?: "0"
    val parentSpanId = record.parentSpanId ?: if (hasTrace) "-" else "0"
    val durationMs = record.durationMs
    val service = record.serviceName
    val environment = record.environment
    val threadName = record.threadName
    val srcComponent = record.sourceComponent
    val srcOperation = record.sourceOperation
    val srcHint = record.sourceLocationHint
    val srcFile = record.sourceFile
    val srcLine = record.sourceLine
    val srcFunction = record.sourceFunction
    val spanLabel = record.spanName ?: record.sourceLocationHint ?: record.sourceOperation ?: record.sourceComponent
    val defaultSpanMsg = defaultSpanMessage(record.logRecordKind, spanLabel)

    val humanMessage = if (record.logRecordKind == LogRecordKind.LOG) {
        record.message
    } else {
        record.message.ifBlank { defaultSpanMsg.orEmpty() }
    }

    val headSnippet = headValue(humanMessage)
    val structured = buildString {
        append("ts=").append(record.timestamp)
        append(" lvl=").append(record.level.name.lowercase())
        if (hasTrace || traceId != "0") {
            append(" trace=").append(traceId)
            append(" span=").append(spanId)
            if (parentSpanId != "0" && parentSpanId != "-") append(" parent=").append(parentSpanId)
        }
        if (record.logRecordKind != LogRecordKind.LOG) {
            append(" kind=").append(record.logRecordKind.name)
        }
        if (spanLabel != null && record.logRecordKind != LogRecordKind.LOG) {
            append(" name=").append(quote(spanLabel))
        }
        if (durationMs != null && record.logRecordKind == LogRecordKind.SPAN_END && durationMs != 0L) {
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
        if (record.loggerName.isNotBlank() && (srcCombined == null || record.loggerName != srcCombined)) {
            append(" log=").append(record.loggerName)
        }
        if (!service.isNullOrBlank() && service != "-") append(" svc=").append(service)
        if (!environment.isNullOrBlank() && environment != "-") append(" env=").append(environment)
        if (!threadName.isNullOrBlank() && threadName != "unknown") append(" thread=").append(quote(threadName))
        if (srcFile != null) append(" file=").append(srcFile)
        if (srcLine != null) append(" line=").append(srcLine)
        if (srcFunction != null) append(" fn=").append(quote(srcFunction))
        record.attributes.forEach { (key, value) ->
            append(' ').append(key).append('=').append(quote(value))
        }
        record.throwable?.let { throwable ->
            append(" throwable=").append(quote(throwable::class.simpleName ?: "Throwable"))
            val rawStack = throwable.stackTraceToString().replace("\r", "").trimEnd('\n')
            val normalizedStack = rawStack
                .lineSequence()
                .mapIndexed { idx, line ->
                    val trimmed = line.trimStart()
                    if (idx == 0) trimmed else "    $trimmed"
                }
                .joinToString("\n")
            val stack = "\n$normalizedStack\n"
            append(" stack_trace=").append(quote(stack))
        }
    }

    return humanMessage to "|{ $structured }|"
}
