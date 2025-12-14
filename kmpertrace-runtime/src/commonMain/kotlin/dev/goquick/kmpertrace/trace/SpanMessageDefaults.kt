package dev.goquick.kmpertrace.trace

import dev.goquick.kmpertrace.core.LogRecordKind

internal fun defaultSpanMessage(kind: LogRecordKind, spanLabel: String?): String? = when (kind) {
    LogRecordKind.SPAN_START -> spanLabel?.let { "+++ $it" }
    LogRecordKind.SPAN_END -> spanLabel?.let { "--- $it" }
    else -> null
}
