package dev.goquick.kmpertrace.cli

import dev.goquick.kmpertrace.cli.ansi.AnsiPalette
import dev.goquick.kmpertrace.cli.ansi.maybeColor

internal fun renderHelp(colorize: Boolean): String {
    val sb = StringBuilder()
    sb.appendLine(maybeColor("KmperTrace TUI — Help", AnsiPalette.header, colorize))
    sb.appendLine()
    sb.appendLine("Keys (when stdin is not the log source):")
    sb.appendLine("  ? : toggle this help")
    sb.appendLine("  c : clear buffer (resets parsed events)")
    sb.appendLine("  r : cycle raw logs (off → debug → info → error → all)")
    sb.appendLine("  l : cycle min level (all → debug → info → error)")
    sb.appendLine("  / : set search focus (hides items not matching term; empty clears)")
    sb.appendLine("  q : quit")
    sb.appendLine("  If raw mode is unavailable, type the letter and press Enter.")
    return sb.toString().trimEnd()
}
