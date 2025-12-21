package dev.goquick.kmpertrace.trace

import kotlin.jvm.JvmInline

/**
 * Safe, human-readable trigger identifier for a user/system journey.
 *
 * The value is intended to be stable and safe to show in trace UIs by default.
 * Characters outside `[A-Za-z0-9_.-]` are replaced with `_`.
 */
@JvmInline
value class TraceTrigger private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        fun tap(target: String): TraceTrigger = fromParts("tap", target)

        fun tab(name: String): TraceTrigger = fromParts("tab", name)

        fun system(event: String): TraceTrigger = fromParts("system", event)

        fun custom(value: String): TraceTrigger = TraceTrigger(sanitize(value))

        private fun fromParts(kind: String, detail: String): TraceTrigger =
            TraceTrigger("$kind.${sanitizeSegment(detail)}")

        private fun sanitize(value: String): String {
            val sanitized = sanitizeSegment(value)
            return if (sanitized.isNotEmpty()) sanitized else "unknown"
        }

        private fun sanitizeSegment(segment: String): String {
            if (segment.isBlank()) return "unknown"

            val replaced = buildString {
                segment.forEach { ch ->
                    append(if (ch in allowedChars) ch else '_')
                }
            }

            val normalized = replaced.trim('_').ifBlank { "unknown" }
            return normalized
        }
    }
}

private val allowedChars: Set<Char> = buildSet {
    ('a'..'z').forEach(::add)
    ('A'..'Z').forEach(::add)
    ('0'..'9').forEach(::add)
    add('_')
    add('-')
    add('.')
}

