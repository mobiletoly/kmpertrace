package dev.goquick.kmpertrace.cli

enum class SpanAttrsMode { OFF, ON }

internal fun validateSpanAttrsMode(value: String) {
    parseSpanAttrsMode(value) // will throw if invalid
}

internal fun parseSpanAttrsMode(value: String?): SpanAttrsMode =
    when (value?.lowercase()) {
        null, "off", "0", "false" -> SpanAttrsMode.OFF
        "on", "1", "true" -> SpanAttrsMode.ON
        else -> throw IllegalArgumentException("Invalid --span-attrs value: $value (use off|on)")
    }

internal fun nextSpanAttrsMode(current: SpanAttrsMode): SpanAttrsMode =
    when (current) {
        SpanAttrsMode.OFF -> SpanAttrsMode.ON
        SpanAttrsMode.ON -> SpanAttrsMode.OFF
    }
