package dev.goquick.kmpertrace.analysis

import dev.goquick.kmpertrace.parse.ParsedEvent

/**
 * Mutable filter state to be shared between analysis core and UI.
 */
data class FilterState(
    val minLevel: String? = null,
    val traceId: String? = null,
    val component: String? = null,
    val operation: String? = null,
    val text: String? = null,
    val excludeUntraced: Boolean = false
) {
    fun predicate(): (ParsedEvent) -> Boolean = { evt ->
        when {
            excludeUntraced && evt.traceId == "0" -> false
            traceId != null && evt.traceId != traceId -> false
            component != null && evt.rawFields["src_comp"] != component -> false
            operation != null && evt.rawFields["src_op"] != operation -> false
            minLevel != null -> {
                val levelOrder = levelOrdinal(evt.rawFields["lvl"])
                val minOrder = levelOrdinal(minLevel)
                levelOrder >= minOrder
            }
            text != null -> {
                val haystacks = listOfNotNull(evt.message, evt.rawFields["stack_trace"]).joinToString("\n")
                haystacks.contains(text, ignoreCase = true)
            }
            else -> true
        }
    }

    fun describe(): String = buildList {
        minLevel?.let { add("lvl>=${it.uppercase()}") }
        traceId?.let { add("trace=$it") }
        component?.let { add("comp=$it") }
        operation?.let { add("op=$it") }
        if (text != null) add("text=$text")
        if (excludeUntraced) add("untraced=off")
    }.joinToString(",")
}

private fun levelOrdinal(lvl: String?): Int = when (lvl?.lowercase()) {
    "verbose", "v" -> 0
    "debug", "d" -> 1
    "info", "i" -> 2
    "warn", "w" -> 3
    "error", "e" -> 4
    "assert", "a" -> 5
    else -> 0
}
