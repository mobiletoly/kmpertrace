package dev.goquick.kmpertrace.trace

import dev.goquick.kmpertrace.core.EventKind

internal fun defaultSpanMessage(kind: EventKind, spanLabel: String?): String? = when (kind) {
    EventKind.SPAN_START -> spanLabel?.let { "+++ $it" }
    EventKind.SPAN_END -> spanLabel?.let { "--- $it" }
    else -> null
}
